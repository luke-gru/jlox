package com.craftinginterpreters.lox;

import java.io.IOException;
import java.util.List;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AstPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {
    private int indent = 0;
    private Resolver resolver = null;
    private Stmt.Class currentClass = null;

    public static boolean silenceErrorOutput = false;
    public static String PARSE_ERROR = "!error!";

    public static void main(String[] args) throws IOException {
        String fname = null;
        String src = null;
        boolean printVarResolution = false;
        int i = 0;

        while (i < args.length) {
            if (args[i].equals("-f")) {
                fname = args[i+1];
                i = i + 2;
            } else if (args[i].equals("-s")) {
                src = args[i+1];
                i = i + 2;
            } else if (args[i].equals("-r")) {
                printVarResolution = true;
                i++;
            } else {
                System.err.println("Usage: AstPrinter [-f FILENAME] [-s SRC]");
                System.exit(1);
            }
        }

        if (src == null) {
            byte[] bytes = Files.readAllBytes(Paths.get(fname));
            src = new String(bytes, Charset.defaultCharset());
        }
        Parser parser = Parser.newFromSource(src);
        boolean silenceErrorsOld = Lox.silenceParseErrors;
        if (silenceErrorOutput) {
            Lox.silenceParseErrors = true;
        }
        List<Stmt> statements = parser.parse();
        if (silenceErrorOutput) {
            Lox.silenceParseErrors = silenceErrorsOld;
        }
        if (parser.getError() != null && !silenceErrorOutput) {
            System.err.println("[Warning]: parse error");
        }
        Resolver resolver = null;
        if (printVarResolution) {
            resolver = new Resolver(new Interpreter());
            resolver.resolve(statements);
        }
        System.out.println("Result:");
        System.out.print(new AstPrinter(resolver).stmtsToString(statements));
    }

    public static String print(String src, boolean printVarResolution) {
        Parser parser = Parser.newFromSource(src);
        boolean silenceErrorsOld = Lox.silenceParseErrors;
        if (silenceErrorOutput) {
            Lox.silenceParseErrors = true;
        }
        List<Stmt> statements = parser.parse();
        if (silenceErrorOutput) {
            Lox.silenceParseErrors = silenceErrorsOld;
        }
        if (parser.getError() != null) {
            return PARSE_ERROR;
        }
        Resolver resolver = null;
        if (printVarResolution) {
            resolver = new Resolver(new Interpreter());
            resolver.resolve(statements);
        }
        return (new AstPrinter(resolver)).stmtsToString(statements);
    }

    AstPrinter(Resolver resolver) {
        this.resolver = resolver;
    }

    public static String print(String src) {
        return print(src, false);
    }

    public String stmtsToString(List<Stmt> stmts) {
        StringBuilder builder = new StringBuilder();
        for (Stmt stmt : stmts) {
            builder.append(stmt.accept(this));
            builder.append("\n");
        }
        return builder.toString();
    }

    public String exprToString(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitExpressionStmt(Stmt.Expression stmt) {
        return indent() + exprToString(stmt.expression);
    }

    @Override
    public String visitPrintStmt(Stmt.Print stmt) {
        return indent() + parenthesize("print", stmt.expression);
    }

    @Override
    public String visitVarStmt(Stmt.Var stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(indent() + "(varDecl");
        for (Token varTok : stmt.names) {
            builder.append(" (" + varTok.lexeme + ")");
        }
        for (Expr init : stmt.initializers) {
            builder.append(" " + init.accept(this));
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitBlockStmt(Stmt.Block blockStmt) {
        StringBuilder builder = new StringBuilder();
        if (blockStmt.statements.size() == 0) {
            return builder.append(indent() + "(block)").toString();
        }
        builder.append(indent() + "(block\n");
        indent++;
        for (Stmt stmt : blockStmt.statements) {
            builder.append(stmt.accept(this));
            builder.append("\n");
        }
        indent--;
        builder.append(indent() + ")");
        return builder.toString();
    }

    @Override
    public String visitIfStmt(Stmt.If stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(indent() + "(if " + stmt.condition.accept(this) + "\n");
        indent++;
        builder.append(stmt.ifBranch.accept(this));
        if (stmt.elseBranch != null) {
            builder.append("\n" + stmt.elseBranch.accept(this));
        }
        indent--;
        builder.append("\n" + indent() + ")");
        return builder.toString();
    }

    @Override
    public String visitWhileStmt(Stmt.While stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(indent() + "(while " + stmt.condition.accept(this) + "\n");
        indent++;
        builder.append(stmt.body.accept(this));
        indent--;
        builder.append("\n" + indent() + ")");
        return builder.toString();
    }

    @Override
    public String visitForStmt(Stmt.For stmt) {
        StringBuilder builder = new StringBuilder();
        String init = "(nop)";
        String test = "(nop)";
        String incr = "(nop)";
        if (stmt.initializer != null) {
            init = stmt.initializer.accept(this).replaceAll("^\\s+", "");
        }
        if (stmt.test != null) {
            test = stmt.test.accept(this);
        }
        if (stmt.increment != null) {
            incr = stmt.increment.accept(this);
        }
        builder.append(indent() + "(for " + init + " " + test + " " + incr + "\n");
        indent++;
        builder.append(stmt.body.accept(this));
        indent--;
        builder.append("\n" + indent() + ")");
        return builder.toString();
    }

    @Override
    public String visitTryStmt(Stmt.Try stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(indent() + "(try\n");
        indent++;
        builder.append(stmt.tryBlock.accept(this));
        for (Stmt.Catch catchStmt : stmt.catchStmts) {
            builder.append("\n" + catchStmt.accept(this));
        }
        indent--;
        builder.append("\n" + indent() + ")");
        return builder.toString();
    }

    @Override
    public String visitCatchStmt(Stmt.Catch stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(indent() + "(catch " + stmt.catchExpr.accept(this));
        if (stmt.catchVar != null) {
            builder.append(" " + stmt.catchVar.accept(this));
        }
        builder.append("\n");
        indent++;
        builder.append(stmt.block.accept(this));
        indent--;
       builder.append("\n"+indent()+")");
        return builder.toString();
    }

    @Override
    public String visitThrowStmt(Stmt.Throw stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(indent() + "(throw " + stmt.throwExpr.accept(this) + ")");
        return builder.toString();
    }

    @Override
    public String visitBreakStmt(Stmt.Break stmt) {
        return indent() + "(break)";
    }

    @Override
    public String visitContinueStmt(Stmt.Continue stmt) {
        return indent() + "(continue)";
    }

    @Override
    public String visitFunctionStmt(Stmt.Function stmt) {
        StringBuilder builder = new StringBuilder();
        String fnReceiver = "";
        String fnDeclStr = "fnDecl";
        if (stmt.type == Parser.FunctionType.CLASS_METHOD) {
            fnReceiver = currentClass.name.lexeme + ".";
        }
        if (stmt.type == Parser.FunctionType.GETTER) {
            fnDeclStr = "getter";
        } else if (stmt.type == Parser.FunctionType.SETTER) {
            fnDeclStr = "setter";
        }
        builder.append(indent() + "(" + fnDeclStr + " " + fnReceiver + stmt.name.lexeme);
        int idx = 0;
        for (Param param : stmt.formals) {
            boolean isSplat = param.isSplatted;
            builder.append(" " + (isSplat ? "*" + param.varName() : param.varName()));
            idx++;
        }
        builder.append("\n");
        indent++;
        builder.append(stmt.body.accept(this));
        indent--;
        builder.append("\n" + indent() + ")");
        return builder.toString();
    }

    @Override
    public String visitSplatCallExpr(Expr.SplatCall expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("*");
        builder.append(expr.expression.accept(this));
        return builder.toString();
    }

    @Override
    public String visitClassStmt(Stmt.Class stmt) {
        Stmt.Class enclosingClass = this.currentClass;
        this.currentClass = stmt;
        StringBuilder builder = new StringBuilder();
        builder.append(indent() + "(classDecl " + stmt.name.lexeme);
        if (stmt.superClass != null) {
            builder.append(" " + stmt.superClass.name.lexeme);
        }
        if (stmt.body.size() == 0) {
            return builder.append(")").toString();
        }
        builder.append("\n");
        indent++;
        for (Stmt statement : stmt.body) {
            builder.append(statement.accept(this));
            builder.append("\n");
        }
        indent--;
        builder.append(indent() + ")");
        this.currentClass = enclosingClass;
        return builder.toString();
    }

    @Override
    public String visitAnonFnExpr(Expr.AnonFn expr) {
        StringBuilder builder = new StringBuilder();
        builder.append(indent() + "(fnAnon");
        int idx = 0;
        for (Param param : expr.formals) {
            boolean isSplat = param.isSplatted;
            builder.append(" " + (isSplat ? "*" + param.varName() : param.varName()));
            idx++;
        }
        builder.append("\n");
        indent++;
        builder.append(expr.body.accept(this));
        indent--;
        builder.append("\n" + indent() + ")");
        return builder.toString();
    }

    @Override
    public String visitReturnStmt(Stmt.Return stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append(indent() + "(return");
        if (stmt.expression != null) {
            builder.append(" " + stmt.expression.accept(this));
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitArrayExpr(Expr.Array expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(array");
        for (Expr el : expr.expressions) {
            builder.append(" ");
            builder.append(el.accept(this));
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitIndexedGetExpr(Expr.IndexedGet expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(indexedget ");
        builder.append(expr.left.accept(this) + " ");
        builder.append(expr.indexExpr.accept(this));
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitIndexedSetExpr(Expr.IndexedSet expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(indexedset ");
        builder.append(expr.left.accept(this) + " ");
        builder.append(expr.indexExpr.accept(this) + " ");
        builder.append(expr.value.accept(this));
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        if (expr.value instanceof String) {
            return "\"" + expr.value.toString() + "\"";
        } else {
            return expr.value.toString();
        }
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme, expr.right);
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(var ").append(expr.name.lexeme);
        if (resolver != null) {
            Integer resolveDist = resolver.interpreter.locals.get(expr);
            String resolveDistStr = "global";
            if (resolveDist != null) {
                resolveDistStr = resolveDist.toString();
            }
            builder.append(" [dist " + resolveDistStr + "]");
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(assign ").append(expr.name.lexeme + " ").
            append(expr.value.accept(this)).append(")");
        return builder.toString();
    }

    @Override
    public String visitCallExpr(Expr.Call expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(call " + expr.left.accept(this));
        for (Expr arg : expr.args) {
            builder.append(" " + arg.accept(this));
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitPropAccessExpr(Expr.PropAccess expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(prop " + expr.left.accept(this) + " ");
        builder.append(expr.property.lexeme + ")");
        return builder.toString();
    }

    @Override
    public String visitPropSetExpr(Expr.PropSet expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(propSet " + expr.object.accept(this) + " ");
        builder.append(expr.property.lexeme + " " +  expr.value.accept(this) + ")");
        return builder.toString();
    }

    @Override
    public String visitThisExpr(Expr.This expr) {
        return "(this)";
    }

    @Override
    public String visitSuperExpr(Expr.Super expr) {
        return "(super " + expr.property.lexeme + ")";
    }

    private String parenthesize(String name, Expr... exprs) {
        StringBuilder builder = new StringBuilder();
        builder.append("(").append(name);
        for (Expr expr : exprs) {
            builder.append(" ");
            builder.append(expr.accept(this));
        }
        builder.append(")");
        return builder.toString();
    }

    private String indent() {
        StringBuilder builder = new StringBuilder();
        int i = indent;
        while (i > 0) {
            builder.append("  ");
            i--;
        }
        return builder.toString();
    }

}
