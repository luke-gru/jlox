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

/**
 * Follows a design based on the paper found here:
 * http://www.bergel.eu/download/papers/Berg07d-debugger.pdf
 */
class Debugger {
    final Interpreter interp;

    static String helpUsage = "Usage:\n" +
        "  next     (n) : step over to next statement or expression\n" +
        "  step     (s) : step into next statement or expression\n" +
        "  print    (p) VARNAME : print variable name\n" +
        "  continue (c) : continue interpreter\n" +
        "  stack    (st) : show stacktrace\n" +
        "  clear    (cls) : clear screen\n" +
        "  lines    (l) [NUMBER] : show source lines\n" +
        "  exit     (e) : exit interpreter\n" +
        "  setbr    (sbr) LINENO : set breakpoint at line\n" +
        "  delbr    (dbr) LINENO : delete breakpoint at line\n" +
        "  help     (h) : show this message\n";

    static Pattern printPat = Pattern.compile("^p(rint)?\\s+(\\w+?);?$");
    static Pattern setbrPat = Pattern.compile("^(setbr|sbr)\\s+(\\d+)\\s*;?$");
    static Pattern delbrPat = Pattern.compile("^(delbr|dbr)\\s+(\\d+)\\s*;?$");
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
        } else if (interp.breakpoints.size() > 0) {
            Integer lineNoInt = interp.lineForNode(interp.currentNode);
            if (lineNoInt == null) {
                System.err.println("[Warning]: debugger couldn't find line for node");
                return false;
            }
            int lineNo = (int)lineNoInt;
            //System.err.println("checking breakpoint at " + String.valueOf(lineNo));
            return interp.breakpoints.contains(lineNoInt);
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
                interp.awaitingOnMap.clear(); // clear step/next map
                this.awaitingPause = interp.breakpoints.size() > 0;
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
                String lineFmt = "%03d";
                if (lineNoEnd > 1000) {
                    lineFmt = "%04d";
                } else if (lineNoEnd < 100) {
                    lineFmt = "%02d";
                }
                for (int i = lineNoStart; i < lineNoEnd; i++) {
                    if (i == lineNo) {
                        srcLines.add(String.format(lineFmt, i) + ": ==> " + lines.get(i-1) + " <==");
                    } else {
                        srcLines.add(String.format(lineFmt, i) + ": " + lines.get(i-1));
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
            Matcher printMatch = printPat.matcher(line);
            if (printMatch.find()) { // print
                String varName = printMatch.group(2);
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
            Matcher setbrMatch = setbrPat.matcher(line);
            if (setbrMatch.find()) { // set breakpoint
                String lineNoStr = setbrMatch.group(2);
                if (lineNoStr == null) {
                    System.err.println("Usage: setbr LINENO");
                }
                int lineNo = Integer.parseInt(lineNoStr);
                boolean didSet = setBreakpointAt(lineNo);
                if (didSet) {
                    out.println("Breakpoint set at line: " + lineNoStr);
                } else {
                    out.println("[Warning]: Breakpoint already set at line: " + lineNoStr);
                }
                continue;
            }
            Matcher delbrMatch = delbrPat.matcher(line);
            if (delbrMatch.find()) { // delete breakpoint
                String lineNoStr = delbrMatch.group(2);
                if (lineNoStr == null) {
                    System.err.println("Usage: delbr LINENO");
                }
                int lineNo = Integer.parseInt(lineNoStr);
                boolean didDel = delBreakpointAt(lineNo);
                if (didDel) {
                    out.println("Breakpoint deleted at line: " + lineNoStr);
                } else {
                    out.println("[Warning]: Breakpoint not set at line: " + lineNoStr);
                }
                continue;
            }
        }
        out.println("-- Ending lox debugger -- ");
        if (!interp.exited) {
            interp.continueInterpreter();
        }
    }

    // NOTE: assumes the interpreter's current running file has at least
    // `lineNo` lines.
    // TODO: check that this line exists for the current file.
    // TODO: Also, cache, per interpreted file, the objectid of the node that
    // we pause at first at breakpoints, so we can actually go the next line
    // when continuing, instead of stopping at the next node that has a token
    // for the same line.
    private boolean setBreakpointAt(int lineNo) {
        if (interp.breakpoints.contains((Integer)lineNo)) {
            return false;
        }
        interp.awaitingOnMap.clear(); // clear step/next map
        interp.breakpoints.add((Integer)lineNo);
        this.awaitingPause = true;
        return true;
    }

    private boolean delBreakpointAt(int lineNo) {
        int idx = interp.breakpoints.indexOf((Integer)lineNo);
        if (idx == -1) {
            return false;
        }
        interp.awaitingOnMap.clear(); // clear step/next map
        interp.breakpoints.remove(idx);
        this.awaitingPause = true;
        return true;
    }
}
