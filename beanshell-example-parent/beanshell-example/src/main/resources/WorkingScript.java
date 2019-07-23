import java.util.ArrayList;

class MyScript {

  public static void main(String[] args) {
    print("main, ctx=" + args[0]);
    testLanguageFeatures();
    print("main finished");
  }

  private static void testLanguageFeatures() {
    print("testLanguageFeatures");
    var list = new ArrayList<String>();
    list.add("test generics");
    print(list);
    for (var i = 0; i < 1; i++) {
      print("Test cycles");
    }
  }
}

MyScript.main(new String[] {ctx});
