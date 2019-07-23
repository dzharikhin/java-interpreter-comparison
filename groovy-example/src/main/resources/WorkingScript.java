import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

void mainMethod(String... args) {
  println("main, ctx=" + args[0]);
  testLanguageFeatures();
  println("main finished");
}

void testLanguageFeatures() {
  println("testLanguageFeatures");
  List list = new ArrayList<String>();
  list.add("test generics");
  println(list);
  for (int i = 0; i < 1; i++) {
    println("Test cycles");
  }
  IntStream.range(0, 1).forEach({i -> println("Test lambdas " + i)});
}
mainMethod(ctx);
