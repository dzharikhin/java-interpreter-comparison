import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class MyScript {
  public static void main(String... args) {
    System.out.println("main, ctx=" + args[0]);
    testLanguageFeatures();
    System.out.println("main finished");
  }

  private static void testLanguageFeatures() {
    System.out.println("testLanguageFeatures");
    var list = new ArrayList<String>();
    list.add("test generics");
    System.out.println(list);
    for (var i = 0; i < 1; i++) {
      System.out.println("Test cycles");
    }
    IntStream.range(0, 1).forEach(i -> System.out.println("Test lambdas " + i));
    System.out.println(List.of("", "test method ref").stream().filter(Predicate.not(String::isEmpty)).collect(Collectors.toList()));
  }
}
