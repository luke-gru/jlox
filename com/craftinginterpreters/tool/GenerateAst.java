package com.craftinginterpreters.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err.println("Usage: generate_ast <output directory>");
      System.exit(1);
    }
    String outputDir = args[0];
    // NOTE: when add new expr class here, make sure to change
    // Interpreter#tokenFromExpr as well as the various visitor methods
    // (compiler will catch visitor methods, though).
    defineAst(outputDir, "Expr", Arrays.asList(
        "Binary   : Expr left, Token operator, Expr right",
        "Logical  : Expr left, Token operator, Expr right",
        "Grouping : Expr expression",
        "Literal  : Object value",
        "Array    : Token lbracket, List<Expr> expressions",
        "IndexedGet : Token lbracket, Expr left, Expr indexExpr",
        "IndexedSet : Token lbracket, Expr left, Expr indexExpr, Expr value",
        "Unary    : Token operator, Expr right",
        "Variable : Token name",
        "Assign   : Token name, Expr value", // TODO: allow multiple assignment
        "Call     : Expr left, List<Expr> args",
        "AnonFn   : Token fun, List<Param> formals, Stmt body",
        "PropAccess : Expr left, Token property",
        "PropSet    : Expr object, Token property, Expr value",
        "This       : Token keyword",
        "Super      : Token keyword, Token property",
        "SplatCall  : Expr expression"
    ));
    defineAst(outputDir, "Stmt", Arrays.asList(
        "Expression : Expr expression",
        "Print      : Expr expression",
        "Var        : List<Token> names, List<Expr> initializers",
        "Block      : List<Stmt> statements",
        "If         : Expr condition, Stmt ifBranch, Stmt elseBranch",
        "While      : Expr condition, Stmt body",
        "For        : Stmt initializer, Expr test, Expr increment, Stmt body",
        "Foreach    : List<Token> variables, Expr obj, Block body",
        "Continue   : Token token, Stmt loopStmt", // in while or for stmt
        "Break      : Token token, Stmt loopStmt", // in while or for stmt
        "Function   : Token name, List<Param> formals, Stmt body, Parser.FunctionType type, Class klass",
        "Return     : Token token, Expr expression",
        "Class      : Token name, Expr.Variable superClassVar, Object superClass, List<Stmt> body",
        "Module     : Token name, List<Stmt> body",
        "Try        : Block tryBlock, List<Catch> catchStmts",
        "Catch      : Expr catchExpr, Expr.Variable catchVar, Block block",
        "Throw      : Token keyword, Expr throwExpr",
        "In         : Expr object, List<Stmt> body"
    ));
  }

  private static void defineAst(String outputDir, String baseName,
          List<String> types) throws IOException {
      String path = outputDir + "/" + baseName + ".java";
      PrintWriter writer = new PrintWriter(path, "UTF-8");

      writer.println("package com.craftinginterpreters.lox;");
      writer.println("");
      writer.println("import java.util.List;");
      writer.println("");

      writer.println("abstract class " + baseName + " {");

      defineVisitor(writer, baseName, types);

      for (String type : types) {
          String className = type.split(":")[0].trim();
          String fields = type.split(":")[1].trim();
          defineType(writer, baseName, className, fields);
      }

      // The base accept() method
      writer.println("");
      writer.println("  abstract <R> R accept(Visitor<R> visitor);");

      writer.println("}");
      writer.close();
  }

  private static void defineVisitor(PrintWriter writer, String baseName, List<String> types) {
      writer.println("  interface Visitor<R> {");

      for (String type : types) {
          String typeName = type.split(":")[0].trim();
          writer.println("    R visit" + typeName + baseName + "(" +
                  typeName + " " + baseName.toLowerCase() + ");");
      }

      writer.println("  }");
  }

  private static void defineType(PrintWriter writer, String baseName, String className, String fieldList) {
      writer.println("  static class " + className + " extends " + baseName + " {");

      // constructor
      writer.println("    " + className + "(" + fieldList + ") {");

      // Store parameters in fields
      String[] fields = fieldList.split(", ");
      for (String field : fields) {
          String name = field.split(" ")[1];
          writer.println("      this." + name + " = " + name + ";");
      }

      writer.println("    }");

      // Visitor pattern
      writer.println();
      writer.println("    <R> R accept(Visitor<R> visitor) {");
      writer.println("      return visitor.visit" + className + baseName + "(this);");
      writer.println("    }");

      // Fields
      writer.println("");
      for (String field : fields) {
          writer.println("    public " + field + ";");
      }

      writer.println("  }");
  }
}
