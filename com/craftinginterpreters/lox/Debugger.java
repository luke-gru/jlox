package com.craftinginterpreters.lox;

import jline.console.ConsoleReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

class Debugger {
    final Interpreter interp;

    static String helpUsage = "Usage:\n" +
        "  next (n) : step over\n" +
        "  step (s) : step into\n" +
        "  print (p) VARNAME : print variable name\n" +
        "  continue (c) : continue interpreter\n" +
        "  stack (st) : show stacktrace\n" +
        "  clear (cls) : clear screen\n" +
        "  lines (l) [NUMBER] : show source lines\n" +
        "  exit (e) : exit interpreter\n" +
        "  help (h) : show this message\n";

    static Pattern printPat = Pattern.compile("^p(rint)?\\s+(\\w+?);?$");
    public boolean awaitingPause = false;

    Debugger(Interpreter interp) {
        this.interp = interp;
    }

    public boolean isAwaitingPause() {
        if (!awaitingPause) return false;
        if (interp.awaitingOnMap.size() > 0) {
            if (interp.awaitingOnMap.containsKey(interp.getVisitLevel())) {
                int visitIdx = interp.awaitingOnMap.get(interp.getVisitLevel());
                return visitIdx == interp.getVisitIdx();
            }
            return false;
        } else {
            return true;
        }
    }

    public void onTracepoint(Object astNode) throws IOException {
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
                this.awaitingPause = false;
                interp.awaitingOnMap.clear();
                break;
            }
            // step into
            if (line.equals("step") || line.equals("s")) {
                int visitLevel = 1;
                if (interp.visitLevels.size() > 0) {
                    visitLevel = interp.visitLevels.lastElement();
                }
                int visitIdxUp = 0;
                int visitIdxNext = 0;
                if (interp.visitIdxs.size() >= 2) {
                    visitIdxUp = interp.visitIdxs.get(interp.visitIdxs.size()-2);
                }
                if (interp.visitIdxs.size() > 0) {
                    visitIdxNext = interp.visitIdxs.lastElement();
                }
                int visitIdxDown = 0;
                int visitLevelDown = visitLevel+1;
                int visitLevelUp = visitLevel-1;
                if (visitLevelUp < 1) visitLevelUp = 1;
                this.awaitingPause = true;
                interp.awaitingOnMap.put(visitLevelDown, visitIdxDown);
                interp.awaitingOnMap.put(visitLevelUp, visitIdxUp);
                interp.awaitingOnMap.put(visitLevel, visitIdxNext);
                break;
            }
            // step over
            if (line.equals("next") || line.equals("n")) {
                int visitLevel = 1;
                if (interp.visitLevels.size() > 0) {
                    visitLevel = interp.visitLevels.lastElement();
                }
                int visitIdxUp = 0;
                int visitIdxNext = 0;
                if (interp.visitIdxs.size() >= 2) {
                    visitIdxUp = interp.visitIdxs.get(interp.visitIdxs.size()-2);
                }
                if (interp.visitIdxs.size() > 0) {
                    visitIdxNext = interp.visitIdxs.lastElement();
                }
                int visitLevelUp = visitLevel-1;
                if (visitLevelUp < 1) visitLevelUp = 1;
                this.awaitingPause = true;
                interp.awaitingOnMap.put(visitLevelUp, visitIdxUp);
                interp.awaitingOnMap.put(visitLevel, visitIdxNext);
                break;
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
            if (line.equals("lines") || line.equals("l")) {
                String fname = interp.runningFile;
                if (fname == null) {
                    out.println("Couldn't get name of current file to show lines (BUG)!");
                    continue;
                }
                Integer curLine = interp.lineForNode(interp.currentNode);
                if (curLine == null) {
                    out.println("Couldn't get line of file (BUG)!");
                    continue;
                }
                int lineNo = (int)curLine;
                List<String> lines = Files.readAllLines(Paths.get(fname), Charset.defaultCharset());
                int lineNoStart = lineNo - 5;
                int lineNoEnd = lineNo + 5;
                if (lineNoStart < 1) lineNoStart = 1;
                if (lineNoEnd > lines.size()) lineNoEnd = lines.size();
                List<String> srcLines = new ArrayList<>();
                for (int i = lineNoStart; i < lineNoEnd; i++) {
                    if (i == lineNo) {
                        srcLines.add("==> " + lines.get(i-1) + " <==");
                    } else {
                        srcLines.add(lines.get(i-1));
                    }
                }
                for (String srcLine : srcLines) {
                    out.println(srcLine);
                }
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
        if (!interp.exited) {
            interp.continueInterpreter();
        }
    }
}
