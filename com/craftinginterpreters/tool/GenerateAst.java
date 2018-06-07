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
        "Binary     : Expr left, Token operator, Expr right",
        "Logical    : Expr left, Token operator, Expr right",
        "Grouping   : Token lparen, Expr expression",
        "Literal    : Token token, Object value",
        "Array      : Token lbracket, List<Expr> expressions",
        "IndexedGet : Token lbracket, Expr left, Expr indexExpr",
        "IndexedSet : Token lbracket, Expr left, Expr indexExpr, Expr value",
        "Unary      : Token operator, Expr right",
        "Variable   : Token name",
        "Assign     : Token name, Expr value", // TODO: allow multiple assignment
        "Call       : Token lparen, Expr left, List<Expr> args",
        "AnonFn     : Token fun, List<Param> formals, Stmt body",
        "PropAccess : Expr left, Token property",
        "PropSet    : Expr object, Token property, Expr value",
        "This       : Token keyword",
        "Super      : Token keyword, Token property, Stmt classOrModStmt",
        "SplatCall  : Token splat, Expr expression",
        "KeywordArg : Token name, Expr expression"
    ));
    defineAst(outputDir, "Stmt", Arrays.asList(
        "Expression : Expr expression",
        "Print      : Token keyword, Expr expression",
        "Var        : Token keyword, List<Token> names, List<Expr> initializers",
        "Block      : Token token, List<Stmt> statements",
        "If         : Token keyword, Expr condition, Stmt ifBranch, Stmt elseBranch",
        "While      : Token keyword, Expr condition, Stmt body",
        "For        : Token keyword, Stmt initializer, Expr test, Expr increment, Stmt body",
        "Foreach    : Token keyword, List<Token> variables, Expr obj, Block body",
        "Continue   : Token keyword, Stmt loopStmt", // in while/for/foreach stmts
        "Break      : Token keyword, Stmt loopStmt", // in while/for/foreach stmts
        "Function   : Token name, List<Param> formals, Stmt body, Parser.FunctionType type, Class klass",
        "Return     : Token keyword, Expr expression",
        "Class      : Token name, Expr.Variable superClassVar, Object superClass, List<Stmt> body",
        "Module     : Token name, List<Stmt> body",
        "Try        : Token keyword, Block tryBlock, List<Catch> catchStmts",
        "Catch      : Token keyword, Expr catchExpr, Expr.Variable catchVar, Block block", // TODO: add token
        "Throw      : Token keyword, Expr throwExpr",
        "In         : Token keyword, Expr object, List<Stmt> body"
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
      writer.println("    void beforeVisit(Object obj);");
      writer.println("    void afterVisit(Object obj);");

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
      writer.println("      visitor.beforeVisit((Object)this);");
      writer.println("      R ret = visitor.visit" + className + baseName + "(this);");
      writer.println("      visitor.afterVisit((Object)this);");
      writer.println("      return ret;");
      writer.println("    }");

      // Fields
      writer.println("");
      for (String field : fields) {
          writer.println("    public " + field + ";");
      }

      writer.println("  }");
  }
}
