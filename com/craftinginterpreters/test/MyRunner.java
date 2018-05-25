package com.craftinginterpreters.test;

import org.junit.*;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import com.craftinginterpreters.test.AstPrinterTest;
import com.craftinginterpreters.test.InterpreterTest;

public class MyRunner {
  public static void main(String[] args) {
    Result result = JUnitCore.runClasses(AstPrinterTest.class, InterpreterTest.class);
    int failures = 0;
    int numTests = result.getRunCount();
    for (Failure failure : result.getFailures()) {
      System.out.println(failure.toString());
      failures++;
    }
    if (failures > 0) {
        System.err.println(String.valueOf(failures) + " failures in " + String.valueOf(numTests) + " total tests.");
        System.exit(failures);
    } else {
        System.out.println("All " + String.valueOf(numTests) + " tests passed!");
        System.exit(0);
    }
  }
}
