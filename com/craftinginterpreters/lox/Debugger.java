package com.craftinginterpreters.lox;

import jline.console.ConsoleReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

class Debugger {
    final Interpreter interp;

    static String helpUsage = "Usage:\n" +
        "  continue (c)\n" +
        "  next (n)\n" +
        "  exit (e)\n" +
        "  help (h)\n" +
        "  clear (cls)\n" +
        "  print (p) varname\n" +
        "  stack (st)\n";

    static Pattern printPat = Pattern.compile("^p(rint)?\\s+(\\w+?);?$");

    Debugger(Interpreter interp) {
        this.interp = interp;
    }

    public void start() throws IOException {
        interp.pauseInterpreter();
        ConsoleReader reader = new ConsoleReader();
        PrintWriter out = new PrintWriter(reader.getOutput());
        out.println("-- Started lox debugger -- ");
        reader.setPrompt("> ");
        String line = null;
        Matcher match = null;
        for (;;) {
            line = reader.readLine();
            if (line == null) { // CTRL-d (EOF)
                break;
            }
            if (line.equals("continue") || line.equals("c")) {
                break;
            }
            if (line.equals("next") || line.equals("n")) {
                interp.interpretNextStatement();
                continue;
            }
            if (line.equals("exit") || line.equals("e")) {
                interp.exitInterpreter(0);
                return;
            }
            if (line.equals("cls") || line.equals("clear")) {
                reader.clearScreen();
                out.println(""); // avoid double-prompt at next input
                continue;
            }
            if (line.equals("help") || line.equals("h")) {
                out.println(helpUsage);
                continue;
            }
            if (line.equals("stack") || line.equals("st")) {
                out.println(interp.stacktrace());
            }
            match = printPat.matcher(line);
            if (match.find()) { // print
                String varName = match.group(2);
                if (varName == null) {
                    System.err.println("Usage: p(rint) VARNAME");
                    continue;
                }
                Object varVal = null;
                try {
                    varVal = interp.environment.get(varName, true, null);
                } catch (RuntimeError e) {
                    System.err.println(e.getMessage());
                    continue;
                }
                out.println("  => " + interp.stringify(varVal));
                continue;
            }
        }
        out.println("-- Ending lox debugger -- ");
        interp.continueInterpreter();
    }
}
