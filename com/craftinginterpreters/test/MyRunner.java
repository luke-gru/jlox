package com.craftinginterpreters.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.*;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import com.craftinginterpreters.test.AstPrinterTest;
import com.craftinginterpreters.test.InterpreterTest;

public class MyRunner {
  public static void main(String[] args) {
    ArrayList<Class> allClasses = new ArrayList<>();
    allClasses.add(AstPrinterTest.class);
    allClasses.add(InterpreterTest.class);
    ArrayList<Class> klassesToRun = new ArrayList<>();
    if (args.length == 0) {
        klassesToRun = allClasses;
    } else {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("AstPrinter")) {
                klassesToRun.add(AstPrinterTest.class);
            } else if (args[i].equals("Interpreter")) {
                klassesToRun.add(InterpreterTest.class);
            }
        }
    }

    if (klassesToRun.size() == 0) {
        System.err.println("No test classes to run!");
        System.exit(1);
    }

    for (Class klass : klassesToRun) {
        System.err.println("Running test class: " + klass.getName());
    }

    Result result = JUnitCore.runClasses(klassesToRun.toArray(new Class[klassesToRun.size()]));
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
