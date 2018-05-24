package com.craftinginterpreters.test;

import org.junit.*;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import com.craftinginterpreters.test.AstPrinterTest;

public class MyRunner {
  public static void main(String[] args) {
    Result result = JUnitCore.runClasses(AstPrinterTest.class);
    int failures = 0;
    for (Failure failure : result.getFailures()) {
      System.out.println(failure.toString());
      failures++;
    }
    if (failures > 0) {
        System.err.println(String.valueOf(failures) + " test failures");
        System.exit(failures);
    } else {
        System.out.println("All tests passed!");
        System.exit(0);
    }
  }
}
