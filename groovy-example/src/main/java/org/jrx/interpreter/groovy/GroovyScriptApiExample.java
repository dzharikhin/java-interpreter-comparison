package org.jrx.interpreter.groovy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;
import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory;

public class GroovyScriptApiExample {
  private static final AtomicBoolean STOP_EXECUTION_SWITCH = new AtomicBoolean();
  private static final Pattern CLASS_PATTERN = Pattern.compile("class\\s.+?\\s?\\{");

  private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
  private static final GroovyScriptEngineFactory KOTLIN_SCRIPT_ENGINE_FACTORY = new GroovyScriptEngineFactory();

  public static void main(String[] args) throws URISyntaxException, IOException, ScriptException {
    var scriptPath = Path.of(GroovyScriptApiExample.class.getResource("/WorkingScript.java").toURI());
    var script = Files.readString(scriptPath, StandardCharsets.UTF_8);
    try {
      System.out.println("Custom output: " + new GroovyScriptApiExample().executeScript(script, "external context"));
    } finally {
      EXECUTOR_SERVICE.shutdownNow();
    }
  }

  public String executeScript(String script, String ctxVariable) throws ScriptException {
    if (CLASS_PATTERN.matcher(script).matches()) {
      throw new ScriptException("Script must contain no class definitions");
    }
    if (script.contains("static")) {
      throw new ScriptException("Script must contain no static definitions");
    }
    try (var os = new ByteArrayOutputStream();
         var out = new PrintWriter(os, true, StandardCharsets.UTF_8)
    ) {

      ScriptEngine scriptEngine = KOTLIN_SCRIPT_ENGINE_FACTORY.getScriptEngine();
      var context = new SimpleScriptContext();
      context.setWriter(out);
      context.setErrorWriter(out);
      var bindings = new SimpleBindings(new HashMap<>(Map.of("ctx", ctxVariable)));
      context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
      scriptEngine.setContext(context);
      var future = EXECUTOR_SERVICE.submit(() -> {
        try {
          return scriptEngine.eval(script);
        } catch (ScriptException e) {
          throw new CompletionException(e);
        }
      });
      while (!future.isDone()) {
        if (STOP_EXECUTION_SWITCH.get()) {
          future.cancel(true);
          EXECUTOR_SERVICE.shutdown();
        }
        Thread.sleep(50);
      }
      Object result = future.get();
      out.write(String.valueOf(result));
      return os.toString(StandardCharsets.UTF_8);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ScriptException(e);
    } catch (IOException | ExecutionException | CancellationException | CompletionException e) {
      throw new ScriptException(e);
    }
  }
}
