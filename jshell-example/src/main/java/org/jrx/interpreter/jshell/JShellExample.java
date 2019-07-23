package org.jrx.interpreter.jshell;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.script.ScriptException;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.execution.LocalExecutionControlProvider;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy.Default.NO_CONSTRUCTORS;

public class JShellExample {
  private static final AtomicBoolean STOP_EXECUTION_SWITCH = new AtomicBoolean();
  private static final String PUBLIC_CLASS = "public class";
  private static final String MAIN_METHOD = "public static void main";
  private static final Pattern CLASS_NAME = Pattern.compile("class\\s([^\\s]+?)\\s");

  public static void main(String[] args) throws URISyntaxException, IOException, ScriptException {
    var scriptPath = Path.of(JShellExample.class.getResource("/WorkingScript.java").toURI());
    var script = Files.readString(scriptPath, StandardCharsets.UTF_8);
    System.out.println("Custom output: " + new JShellExample().executeScript(script, "external context"));
  }

  public String executeScript(String script, String ctxVariable) throws ScriptException {
    if (script.contains(PUBLIC_CLASS)) {
      throw new ScriptException("There must be no public classes");
    }
    if (!script.contains(MAIN_METHOD)) {
      throw new ScriptException("There must be a " + MAIN_METHOD + " method");
    }
    var scriptMainClassname = ofNullable(CLASS_NAME.matcher(script)).filter(Matcher::find).map(matcher -> matcher.group(1))
      .orElseThrow(() -> new ScriptException(String.join(System.lineSeparator(), "Can not find class name in:", script)));
    try (var outputStream = new ByteArrayOutputStream();
         var output = new PrintStream(outputStream, true, StandardCharsets.UTF_8);
         var shell = createShell(output)
    ) {
      var contextClass = generateCtx(ctxVariable, output);
      var inputLines = List.of(
        "Class<?> contextClass = jdk.jshell.JShell.class.getClassLoader()"
          + ".loadClass(\"" + contextClass.getName() + "\");",
        "var ctx = (String) contextClass.getMethod(\"getContext\").invoke(null);",
        "var out = (java.io.PrintStream) contextClass.getMethod(\"getPrintStream\").invoke(null);",
        //doesn't work inside script class
        "public static void println(Object arg) {\n\tout.println(arg);\n};",
        script,
//        to break shell just uncomment and switch to Script.java
//        scriptMainClassname + ".setCtx(ctx);"
        scriptMainClassname + ".main(ctx);"
        );

      shell.onSnippetEvent(event -> {
        System.out.println("Got event: " + event);
        if (STOP_EXECUTION_SWITCH.get()) {
          System.out.println("Trying to stop sh=" + shell);
          shell.stop();
        }
      });
      shell.onShutdown(sh -> System.out.println("Execution finished. Shutting down, sh=" + sh));
      System.out.println("Starting execution, sh=" + shell);
      for (var line : inputLines) {
        var invalidEvents = getInvalidEvents(shell, shell.eval(line));
        if (!invalidEvents.isEmpty()) {
          throw new ScriptException(invalidEvents.stream().map(event -> formatEventToMessage(shell, event, outputStream))
            .collect(joining(System.lineSeparator())));
        }
      }
      return outputStream.toString();
    } catch (IOException e) {
      throw new ScriptException(e);
    }
  }

  private static Set<SnippetEvent> getInvalidEvents(JShell shell, List<SnippetEvent> events) {
    return events.stream()
      .filter(event -> event.exception() != null
        || !Set.of(Snippet.Status.VALID, Snippet.Status.OVERWRITTEN).contains(shell.status(event.snippet())))
      .collect(toSet());
  }

  private static JShell createShell(PrintStream out) {
    return JShell.builder()
      .executionEngine(new LocalExecutionControlProvider(), Map.of())
      //doesn't work
      .out(out)
      //doesn't work
      .err(out)
      .build();
  }

  private static String formatEventToMessage(JShell jShell, SnippetEvent invalidEvent, ByteArrayOutputStream out) {
    var diagnostics = jShell.diagnostics(invalidEvent.snippet())
      .map(diag -> diag.getMessage(Locale.getDefault()))
      .map(String::strip)
      .filter(Predicate.not(String::isEmpty))
      .collect(Collectors.toList());
    var textParts = List.of(
      Optional.of("Failed to execute " + invalidEvent.snippet()),
      Optional.of(diagnostics)
        .filter(Predicate.not(Collection::isEmpty))
        .map(diag -> diag.stream().collect(joining(System.lineSeparator(), "Diagnostic messages: " + System.lineSeparator(), ""))),
      Optional.of(out.toString()).map(String::strip).filter(Predicate.not(String::isEmpty))
        .map(s -> "Collected output: " + System.lineSeparator() + s),
      ofNullable(invalidEvent.exception()).map(ex -> {
        try (var os = new ByteArrayOutputStream();
          var printStream = new PrintStream(os)) {
          ex.printStackTrace(printStream);
          return os.toString();
        } catch (IOException e) {
          return null;
        }
      })
    );
    return textParts.stream().filter(Optional::isPresent).map(Optional::get).collect(joining(System.lineSeparator()));
  }

  private static Class<?> generateCtx(String ctx, PrintStream out) {
    var className = "Ctx_" + UUID.randomUUID().toString().replaceAll("-", "");
    return new ByteBuddy()
      .subclass(Object.class, NO_CONSTRUCTORS)
      .modifiers(Visibility.PUBLIC)
      .name(className)
      .defineMethod("getContext", String.class, Visibility.PUBLIC, Ownership.STATIC)
      .intercept(FixedValue.reference(ctx))
      .defineMethod("getPrintStream", PrintStream.class, Visibility.PUBLIC, Ownership.STATIC)
      .intercept(FixedValue.reference(out))
      .make()
      .load(JShell.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
      .getLoaded();
  }

}
