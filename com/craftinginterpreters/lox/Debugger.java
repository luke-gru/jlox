package com.craftinginterpreters.lox;

import jline.console.ConsoleReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

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
        "  vars     (va) [LEVEL] : print variable names and values in scope.\n" +
        "           LEVEL defaults to 0, local scope only. -1 for all scopes,\n" +
        "           1 for local and enclosing scope, etc.\n" +
        "  continue (c) : continue interpreter\n" +
        "  stack    (st) : show stacktrace\n" +
        "  clear    (cls) : clear screen\n" +
        "  lines    (l) [NUMBER] : show source lines\n" +
        "  exit     (e) : exit interpreter\n" +
        "  setbr    (sbr) LINENO : set breakpoint at line\n" +
        "  delbr    (dbr) LINENO : delete breakpoint at line\n" +
        "  eval     (e) CODE : evaluate code\n" +
        "  help     (h) : show this message\n";

    static Pattern printPat = Pattern.compile("^p(rint)?\\s+(\\w+?);?$");
    static Pattern varsPat = Pattern.compile("^va(rs)?\\s*(-?\\d*).*$");
    static Pattern setbrPat = Pattern.compile("^(setbr|sbr)\\s+(\\d+)\\s*;?$");
    static Pattern delbrPat = Pattern.compile("^(delbr|dbr)\\s+(\\d+)\\s*;?$");
    static Pattern evalPat = Pattern.compile("^(eval|e)\\s+(.+)$");
    public boolean awaitingPause = false;

    // Map of {filename => {lineno => AST node}} that we break on for breakpoints.
    // Used because if we're caught on a breakpoint and enter "next", we won't get
    // caught on the same breakpoint again even if the next statement/expression
    // evaluated is on the same line (the node is different).
    private static Map<String,Map<Integer,Object>> breakptMap = new HashMap<>();

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
            //System.err.println("checking breakpoint at " + String.valueOf(lineNo));
            if (couldBeInBreakpoint()) {
                return registerNodeForBreakpoint();
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    public void onTracepoint(Object astNode) throws IOException {
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
                    visitIdxNext = interp.visitIdxs.lastElement()+1;
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
                    visitIdxNext = interp.visitIdxs.lastElement()+1;
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

            Matcher varsMatch = varsPat.matcher(line);
            if (varsMatch.find()) { // show variable names and values in specified scope(s)
                String scopeNumStr = varsMatch.group(2);
                int scopeNum = 0;
                if (scopeNumStr != null && !scopeNumStr.equals("")) {
                    scopeNum = Integer.parseInt(scopeNumStr);
                }
                if (scopeNum < 0) {
                    scopeNum = -1; // use all scopes
                }
                List<String> outputLines = new ArrayList<>();

                Environment curEnv = interp.environment; // local (current) scope
                int i = (scopeNum == -1 ? 10000 : scopeNum+1);
                int scopeIdx = 0;
                while (curEnv != null && i > 0) {
                    String scopeStr = "";
                    if (curEnv.enclosing == null) {
                        scopeStr = " (global)";
                    } else if (scopeIdx == 0) {
                        scopeStr = " (local)";
                    }
                    Iterator iter = curEnv.values.entrySet().iterator();
                    boolean hasAtLeast1Var = false;
                    while (iter.hasNext()) {
                        Map.Entry<String,Object> pair = (Map.Entry<String,Object>)iter.next();
                        String key = pair.getKey();
                        Object val = pair.getValue();
                        // don't show top-level native functions
                        if (val instanceof LoxNativeCallable) {
                            continue;
                        }
                        // don't show native classes
                        if (val instanceof LoxNativeClass) {
                            continue;
                        }
                        // don't show native modules
                        if (val instanceof LoxNativeModule) {
                            continue;
                        }
                        outputLines.add("\t" + key + " => " + interp.stringify(val) + " (" +
                                interp.nativeTypeof(null, val) + ")");
                        hasAtLeast1Var = true;
                    }
                    String suffixStr = "";
                    if (!hasAtLeast1Var) {
                        suffixStr = " [None]";
                    }
                    outputLines.add("Scope " + String.valueOf(scopeIdx) + scopeStr + ":" +
                            suffixStr);
                    curEnv = curEnv.enclosing;
                    i--;
                    scopeIdx++;
                }

                // show outer scope(s) first, so they show above the innermost
                // scope on the console
                Collections.reverse(outputLines);

                for (String ln : outputLines) {
                    out.println(ln);
                }
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

            Matcher evalMatch = evalPat.matcher(line);
            if (evalMatch.find()) {
                String src = evalMatch.group(2);
                Scanner scanner = new Scanner(src);
                scanner.scanUntilEnd();
                while (scanner.inBlock > 0) {
                    String promptStr = "> ";
                    for (int i = scanner.inBlock; i > 0; i--) {
                        promptStr += "  ";
                    }
                    reader.setPrompt(promptStr);
                    line = reader.readLine();
                    scanner.appendSrc(line);
                    scanner.scanUntilEnd();
                    src += ("\n" + line);
                }
                reader.setPrompt("> ");
                boolean oldAwaitingPause = this.awaitingPause;
                // set to false, otherwise we might end up back at the debugger by
                // interpreting the line
                this.awaitingPause = false;
                Object ret = null;
                try {
                    ret = interp.evalSrc(src);
                } catch (Throwable e) {
                    out.println("Error evaluating code: " + e.toString());
                    this.awaitingPause = oldAwaitingPause;
                    continue;
                }
                this.awaitingPause = oldAwaitingPause;
                out.println("  => " + interp.stringify(ret));
                continue;
            }

            // invalid command
            out.println("[Warning]: invalid command, 'help' for usage details");
        }
        out.println("-- Ending lox debugger -- ");
    }

    // NOTE: assumes the interpreter's current running file has at least
    // `lineNo` lines.
    // TODO: check that this line exists for the current file.
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
        deregisterNodeForBreakpoint(lineNo);
        this.awaitingPause = true;
        return true;
    }

    private String getCurrentFilename() {
        if (interp.runningFile == null) {
            return "<unknown>";
        } else {
            return interp.runningFile;
        }
    }

    private boolean couldBeInBreakpoint() {
        if (!this.awaitingPause) return false;
        if (interp.awaitingOnMap.size() > 0) return false;
        Integer lineNoInt = interp.lineForNode(interp.currentNode);
        if (lineNoInt == null) return false;
        return interp.breakpoints.contains(lineNoInt);
    }

    private boolean registerNodeForBreakpoint() {
        String fname = getCurrentFilename();
        Map innerMap = breakptMap.get(fname);
        Integer lineNoInt = interp.lineForNode(interp.currentNode);
        if (innerMap == null) {
            innerMap = new HashMap<Integer,Object>();
            breakptMap.put(fname, innerMap);
        }
        LoxUtil.Assert(lineNoInt != null);
        if (innerMap.containsKey(lineNoInt)) {
            return innerMap.get(lineNoInt) == interp.currentNode;
        }
        innerMap.put(lineNoInt, interp.currentNode);
        return true;
    }

    private void deregisterNodeForBreakpoint(int lineNo) {
        String fname = getCurrentFilename();
        Map innerMap = breakptMap.get(fname);
        if (innerMap == null) {
            return;
        }
        if (!innerMap.containsKey((Integer)lineNo)) {
            return;
        }
        innerMap.remove((Integer)lineNo);
    }
}
