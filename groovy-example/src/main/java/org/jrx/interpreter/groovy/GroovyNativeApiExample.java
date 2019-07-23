package org.jrx.interpreter.groovy;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.transform.ConditionalInterrupt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.script.ScriptException;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import static org.codehaus.groovy.control.messages.WarningMessage.PARANOIA;

public class GroovyNativeApiExample {
  private static final AtomicBoolean STOP_EXECUTION_SWITCH = new AtomicBoolean();
  private static final Pattern SYSTEM_OUT_PATTERN = Pattern.compile("System\\.out\\.|System\\.err\\.");
  private static final String STOP_EXECUTION_SWITCH_NAME = "stopExecutionSwitch";

  public static void main(String[] args) throws URISyntaxException, ScriptException, IOException {
    var scriptPath = Path.of(GroovyNativeApiExample.class.getResource("/WorkingScript.java").toURI());
    var script = Files.readString(scriptPath, StandardCharsets.UTF_8);
    System.out.println("Custom output: " + new GroovyNativeApiExample().executeScript(script, "external context"));
  }

  public String executeScript(String script, String ctxVariable) throws ScriptException {
    try (var os = new ByteArrayOutputStream();
         var out = new PrintWriter(os, true, StandardCharsets.UTF_8)
    ) {

      var compilerConfig = new CompilerConfiguration();
      compilerConfig.setSourceEncoding(StandardCharsets.UTF_8.name());
      compilerConfig.setVerbose(true);
      compilerConfig.setWarningLevel(PARANOIA);
      compilerConfig.setDebug(true);
      var stopAllClosure = GeneralUtils.closureX(GeneralUtils.returnS(GeneralUtils.callX(GeneralUtils.varX(STOP_EXECUTION_SWITCH_NAME), "get")));
      compilerConfig.addCompilationCustomizers(
        new ASTTransformationCustomizer(Map.of(
          "value", stopAllClosure,
          "thrown", RuntimeException.class
        ), ConditionalInterrupt.class),
        new ASTTransformationCustomizer(new PrintMethodEnrichTransformation())
      );
      var binding = new Binding();
      binding.setProperty(STOP_EXECUTION_SWITCH_NAME, STOP_EXECUTION_SWITCH);
      binding.setProperty("out", out);
      binding.setProperty("err", out);
      binding.setProperty("ctx", ctxVariable);
      var shell = new GroovyShell(GroovyNativeApiExample.class.getClassLoader(), binding, compilerConfig);
      var compiledScript = shell.parse(SYSTEM_OUT_PATTERN.matcher(script).replaceAll(""));
      compiledScript.run();
      return os.toString(StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      throw new ScriptException(e);
    }
  }

  @GroovyASTTransformation
  public static class PrintMethodEnrichTransformation implements ASTTransformation {

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
      Optional.ofNullable(source).ifPresent(src ->
          src.getAST().getClasses().forEach(clazz ->
              clazz.getMethods("println").forEach(methodNode ->
                  clazz.addMethod("print", methodNode.getModifiers(), methodNode.getReturnType(), methodNode.getParameters(),
                    methodNode.getExceptions(),
                    GeneralUtils.stmt(GeneralUtils.callThisX("println", GeneralUtils.args(methodNode.getParameters())))
                  )
//          new AstBuilder().buildFromString("class Stub {\n" +
//            "  public void print(Object arg) {\n" +
//            "    println(arg);\n" +
//            "  }\n" +
//            "  public void print() {\n" +
//            "    println();\n" +
//            "  }\n" +
//            '}').stream()
//            .filter(node -> node instanceof ClassNode)
//            .map(node -> (ClassNode) node).flatMap(classNode -> classNode.getMethods("print").stream()).forEach(clazz::addMethod);
              )
          )
      );
    }
  }
}
