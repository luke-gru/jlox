package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;

/*
 * Grammar:
 * low prec
 * to
 * high prec
 *
 * program        : declaration* EOF ;
 * declaration    : varDecl
 *                | funDecl
 *                | classDecl
 *                | statement ;
 *
 * varDecl        : "var" IDENTIFIER ( "=" expression )? ";"
 * funDecl        : "fun" IDENTIFIER "(" parameterList? ")" blockStmt ;
 *
 * classDecl      : "class" IDENTIFIER ( "<" IDENTIFIER )? "{" classBody "}" ;
 * parameterList  : ( IDENTIFIER "," )* lastParameter
 *                | lastParameter  ;
 *
 * lastParameter  : IDENTIFIER ;
 * classBody      : ( methodDecl | getterDecl | setterDecl )*
 * methodDecl     : IDENTIFIER "(" parameterList? ")" blockStmt
 *                | "class" IDENTIFIER "(" parameterList? ")" blockStmt
 *                ;
 * getterDecl     : IDENTIFIER blockStmt ;
 * setterDecl     : IDENTIFIER "=" "(" parameter ")" blockStmt ;
 * statement      : exprStmt
 *                | printStmt
 *                | blockStmt
 *                | ifStmt
 *                | whileStmt
 *                | forStmt
 *                | continueStmt
 *                | breakStmt ;
 *
 * exprStmt       : expression ";" ;
 * printStmt      : "print" expression ";" ;
 * blockStmt      : "{" declaration* "}"
 * ifStmt         : "if" "(" expression ")" statement ( "else" statement )? ;
 * whileStmt      : "while" "(" expression ")" statement ;
 * forStmt        : "for" "(" (varDecl | exprStmt | ";") expression? ";" expression? ")" statement ;
 * continueStmt   : "continue" ";" ;
 * breakStmt      : "break" ";" ;
 * expression     : assignment ;
 * assignment     : ( call "." )? IDENTIFIER "=" assignment
 *                | logic_or ;
 * logic_or       : logic_and ("or" logic_and)* ;
 * logic_and      : equality ("and" equality)* ;
 * equality       : comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     : addition ( ( ">" | ">=" | "<" | "<=" ) addition )* ;
 * addition       : multiplication ( ( "-" | "+" ) multiplication )* ;
 * multiplication : unary ( ( "/" | "*" ) unary )* ;
 * unary          : ( "!" | "-" ) unary
 *                | call ;
 * call           : primary ( "(" arguments? ")" | "." IDENTIFIER )*
 * arguments      : (expression ",")+
 * primary        : NUMBER | STRING | "false" | "true" | "nil" | "this" | "super" "." IDENTIFIER
                  | IDENTIFIER | anonFn | "(" expression ")" ;
 * anonFn         : "fun" "(" parameterList? ")" blockStmt ;
 */

class Parser {
    public static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;
    private ParseError lastError = null;
    private Stmt inLoopStmt = null;
    private Stmt.Function currentFn = null;
    public Map<String, Stmt.Class> classMap = new HashMap<>();
    private Stmt.Class currentClass = null;

    public enum FunctionType {
        NONE,
        FUNCTION,
        METHOD,
        CLASS_METHOD,
        INITIALIZER,
        GETTER,
        SETTER
    }

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    static Parser newFromSource(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        return new Parser(tokens);
    }

    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            Stmt decl = declaration();
            if (decl != null) {
                statements.add(decl);
            }
        }
        return statements;
    }

    public void reset() {
        current = 0;
        lastError = null;
    }

    public ParseError getError() {
        return lastError;
    }

    private Stmt declaration() {
        try {
            if (matchAny(VAR)) return varDeclaration();
            if (checkTok(FUN) && peekTokN(1).type == IDENTIFIER) {
                advance();
                return funDeclaration(FunctionType.FUNCTION);
            }
            if (matchAny(CLASS)) {
                Token name = consumeTok(IDENTIFIER, "Expected identifier after 'class' keyword");
                if (classMap.containsKey(name.lexeme)) {
                    throw error(name, "Class " + name.lexeme + " already defined");
                }
                Token superName = null;
                Stmt.Class superClassStmt = null;

                if (matchAny(LESS)) {
                    superName = consumeTok(IDENTIFIER, "Expected class name after '<'");
                    if (!classMap.containsKey(superName.lexeme)) {
                        throw error(superName, "Class " + superName.lexeme + " must be defined before being inherited from");
                    }
                    superClassStmt = classMap.get(superName.lexeme);
                }
                consumeTok(LEFT_BRACE, "Expected '{' after class name");
                Stmt.Class enclosingClass = this.currentClass;
                Stmt.Class classStmt = new Stmt.Class(name, superClassStmt, null);
                this.currentClass = classStmt;
                List<Stmt> classBody = classBody();
                classStmt.body = classBody;
                classMap.put(name.lexeme, classStmt);
                this.currentClass = enclosingClass;
                return classStmt;
            }
            return statement();
        } catch (ParseError error) {
            lastError = error;
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consumeTok(IDENTIFIER, "Expected variable name in 'var' declaration.");
        if (this.currentFn != null) {
            for (Token param : this.currentFn.formals) {
                if (param.lexeme.equals(name.lexeme)) {
                    throw error(
                            name,
                            "'var' assignment error: can't shadow parameter '" + name.lexeme + "' in function <" +
                            this.currentFn.name.lexeme + ">"
                            );
                }
            }
        }

        Expr initializer = null;
        if (matchAny(EQUAL)) {
            initializer = expression();
        }

        consumeTok(SEMICOLON, "Expected ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt funDeclaration(FunctionType fnType) {
        Token name = consumeTok(IDENTIFIER, "Expected function name after 'fun' keyword.");
        consumeTok(LEFT_PAREN, "Expected '(' after function name.");
        List<Token> formals = new ArrayList<>();
        while (matchAny(IDENTIFIER)) {
            Token parameter = prevTok();
            formals.add(parameter);
            if (peekTok().type == RIGHT_PAREN) {
                break;
            } else {
                consumeTok(COMMA, "Expected ',' in function parameter list.");
            }
        }
        consumeTok(RIGHT_PAREN, "Expected ')' to end function parameter list.");
        Stmt body = null;
        Stmt.Class klass = null;
        if (fnType != FunctionType.FUNCTION) {
            klass = this.currentClass;
        }
        Stmt.Function fnStmt = new Stmt.Function(name, formals, null, fnType, klass);
        if (matchAny(LEFT_BRACE)) {
            Stmt.Function oldFn = this.currentFn;
            try {
                this.currentFn = fnStmt;
                body = new Stmt.Block(blockStmts());
                fnStmt.body = body;
            } finally {
                this.currentFn = oldFn;
            }
        } else {
            consumeTok(LEFT_BRACE, "Expected { after function parameter list");
        }
        return fnStmt;
    }

    private Stmt getterDeclaration() {
        Token name = consumeTok(IDENTIFIER, "Expected getter name in getter decl");
        consumeTok(LEFT_BRACE, "Expected '{' after getter name in getter decl");
        FunctionType fnType = FunctionType.GETTER;
        Stmt.Function fnStmt = new Stmt.Function(name, new ArrayList<Token>(), null, fnType, this.currentClass);
        this.currentFn = fnStmt;
        Stmt body = new Stmt.Block(blockStmts());
        fnStmt.body = body;
        this.currentFn = null;
        return fnStmt;
    }

    private Stmt setterDeclaration() {
        Token name = consumeTok(IDENTIFIER, "Expected setter name in setter decl");
        consumeTok(EQUAL, "Expected '=' after setter name in setter decl");
        FunctionType fnType = FunctionType.SETTER;
        List<Token> formals = new ArrayList<>();
        consumeTok(LEFT_PAREN, "Expected '(' after '=' in setter decl");
        Token param = consumeTok(IDENTIFIER, "Expected parameter in setter decl");
        formals.add(param);
        consumeTok(RIGHT_PAREN, "Expected ')' after parameter in setter decl");
        consumeTok(LEFT_BRACE, "Expected '{' after parameter in setter decl");
        Stmt.Function fnStmt = new Stmt.Function(name, formals, null, fnType, this.currentClass);
        this.currentFn = fnStmt;
        Stmt body = new Stmt.Block(blockStmts());
        fnStmt.body = body;
        this.currentFn = null;
        return fnStmt;
    }

    private List<Stmt> classBody() {
        List<Stmt> body = new ArrayList<>();
        while (true) {
            // regular method
            if (checkTok(IDENTIFIER) && peekTokN(1).type == LEFT_PAREN) {
                Stmt stmt = funDeclaration(FunctionType.METHOD);
                body.add(stmt);
            // static method
            } else if (checkTok(CLASS) && peekTokN(2).type == LEFT_PAREN) {
                advance();
                if (!checkTok(IDENTIFIER)) {
                    consumeTok(IDENTIFIER, "Expected identifier after keyword 'class' in static method declaration");
                }
                Stmt stmt = funDeclaration(FunctionType.CLASS_METHOD);
                body.add(stmt);
            } else if (checkTok(IDENTIFIER) && peekTokN(1).type == LEFT_BRACE) {
                Stmt stmt = getterDeclaration();
                body.add(stmt);
            } else if (checkTok(IDENTIFIER) && peekTokN(1).type == EQUAL) {
                Stmt stmt = setterDeclaration();
                body.add(stmt);
            } else {
                break;
            }
        }
        consumeTok(RIGHT_BRACE, "Expected '}' after class declaration");
        return body;
    }

    private Stmt statement() {
        if (matchAny(PRINT)) return printStatement();
        if (matchAny(LEFT_BRACE)) return new Stmt.Block(blockStmts());
        if (matchAny(IF)) {
            consumeTok(LEFT_PAREN, "Expected '(' after keyword 'if'.");
            Expr cond = expression();
            consumeTok(RIGHT_PAREN, "Expected ')' after condition in 'if' statement.");
            Stmt if_stmt = statement();
            Stmt else_stmt = null;
            if (matchAny(ELSE)) {
                else_stmt = statement();
            }
            return new Stmt.If(cond, if_stmt, else_stmt);
        }
        if (matchAny(WHILE)) {
            consumeTok(LEFT_PAREN, "Expected '(' after keyword 'while'");
            Expr cond = expression();
            consumeTok(RIGHT_PAREN, "Expected ')' after 'while' condition");
            Stmt oldInLoopStmt = this.inLoopStmt;
            Stmt body;
            Stmt.While whileStmt;
            try {
                whileStmt = new Stmt.While(cond, null);
                this.inLoopStmt = whileStmt;
                body = statement();
                whileStmt.body = body;
            } finally {
                this.inLoopStmt = oldInLoopStmt;
            }
            return (Stmt)whileStmt;
        }
        if (matchAny(FOR)) {
            consumeTok(LEFT_PAREN, "expected '(' after keyword 'for'");
            Stmt initializer = null;
            if (peekTok().type != SEMICOLON) {
                if (matchAny(VAR)) {
                    initializer = varDeclaration();
                } else {
                    initializer = expressionStatement();
                }
            } else {
                advance();
            }
            Expr test = null;
            if (peekTok().type != SEMICOLON) {
                test = expression();
                consumeTok(SEMICOLON, "Expected ';' in 'for' statement after test part");
            } else {
                advance();
            }
            Expr increment = null;
            if (matchAny(RIGHT_PAREN)) {
                // do nothing
            } else {
                increment = expression();
                consumeTok(RIGHT_PAREN, "Expected ')' in 'for' statement after increment part");
            }
            Stmt.For forStmt = new Stmt.For(initializer, test, increment, null);
            Stmt oldInLoopStmt = this.inLoopStmt;
            this.inLoopStmt = forStmt;
            Stmt body = statement();
            this.inLoopStmt = oldInLoopStmt;
            forStmt.body = body;
            return forStmt;
        }
        if (matchAny(CONTINUE)) {
            Token contTok = prevTok();
            if (inLoopStmt == null) {
                throw error(peekTok(), "Keyword 'continue' can only be used in 'while' or 'for' statements.");
            }
            consumeTok(SEMICOLON, "Expected ';' after keyword 'continue'");
            return new Stmt.Continue(contTok, inLoopStmt);
        }
        if (matchAny(BREAK)) {
            Token breakTok = prevTok();
            if (inLoopStmt == null) {
                throw error(peekTok(), "Keyword 'break' can only be used in 'while' or 'for' statements");
            }
            consumeTok(SEMICOLON, "Expected ';' after keyword 'break'");
            return new Stmt.Break(breakTok, inLoopStmt);
        }
        if (matchAny(RETURN)) {
            Token retTok = prevTok();
            Expr expression = null;
            if (matchAny(SEMICOLON)) {
                // do nothing
            } else {
                expression = expression();
                consumeTok(SEMICOLON, "Expected ';' after return expression");
            }
            return new Stmt.Return(retTok, expression);
        }
        return expressionStatement();
    }

    private List<Stmt> blockStmts() {
        List<Stmt> statements = new ArrayList<>();
        while (!checkTok(RIGHT_BRACE)) {
            statements.add(declaration());
        }
        consumeTok(RIGHT_BRACE, "Expected '}' after block statement");
        return statements;
    }

    private Stmt printStatement() {
        Expr value = expression();
        consumeTok(SEMICOLON, "Expected ';' after 'print' expression.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr value = expression();
        consumeTok(SEMICOLON, "Expected ';' after expression.");
        return new Stmt.Expression(value);
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = logicOr();
        if (matchAny(EQUAL)) {
            Token equals = prevTok();
            Expr value = assignment();
            if (expr instanceof Expr.PropAccess) {
                Expr.PropAccess propAccess = (Expr.PropAccess)expr;
                return new Expr.PropSet(propAccess.left, propAccess.property, value);
            } else if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }
            throw error(equals, "Invalid assignment target, must be variable or property name");
        }
        return expr;
    }

    private Expr logicOr() {
        Expr expr = logicAnd();
        while (matchAny(OR)) {
            Token operator = prevTok();
            Expr right = logicAnd();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr logicAnd() {
        Expr expr = equality();
        while (matchAny(AND)) {
            Token operator = prevTok();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();

        while (matchAny(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = prevTok();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = addition();

        while (matchAny(GREATER, GREATER_EQUAL,
                    LESS, LESS_EQUAL)) {
            Token operator = prevTok();
            Expr right = addition();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr addition() {
        Expr expr = multiplication();

        while (matchAny(MINUS, PLUS)) {
            Token operator = prevTok();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr multiplication() {
        Expr expr = unary();

        while (matchAny(STAR, SLASH)) {
            Token operator = prevTok();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr unary() {
        if (matchAny(BANG, MINUS)) {
            Token operator = prevTok();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    private Expr call() {
        Expr expr = primary();
        List<Expr> args = new ArrayList<>();
        while (true) {
            if (matchAny(LEFT_PAREN)) {
                if (matchAny(RIGHT_PAREN)) {
                    // no args
                } else {
                    Expr arg;
                    while (true) {
                        arg = expression();
                        args.add(arg);
                        if (matchAny(COMMA)) {
                            // continue parsing arguments
                        } else {
                            consumeTok(RIGHT_PAREN, "Expected ')' at end of argument list");
                            break;
                        }
                    }
                }
                expr = new Expr.Call(expr, args);
            } else if (checkTok(DOT)) {
                advance();
                Token name = consumeTok(IDENTIFIER, "Expected identifier after '.' in object property access");
                expr = new Expr.PropAccess(expr, name);
            } else {
                break;
            }
        }
        return expr;
    }

    private Expr primary() {
        if (matchAny(FALSE)) return new Expr.Literal(false);
        if (matchAny(TRUE)) return new Expr.Literal(true);
        if (matchAny(NIL)) return new Expr.Literal(null);

        if (matchAny(NUMBER, STRING)) {
            return new Expr.Literal(prevTok().literal);
        }

        if (matchAny(LEFT_PAREN)) {
            Expr expr = expression();
            consumeTok(RIGHT_PAREN, "Expected ')' after group expression.");
            return new Expr.Grouping(expr);
        }

        if (matchAny(THIS)) return new Expr.This(prevTok());
        if (matchAny(SUPER)) {
            Token superTok = prevTok();
            consumeTok(DOT, "Expected '.' after 'super' keyword");
            Token propName = consumeTok(IDENTIFIER, "Expected identifier after 'super.'");
            Expr superExpr = new Expr.Super(superTok, propName);
            return superExpr;
        }

        if (matchAny(IDENTIFIER)) {
            return new Expr.Variable(prevTok());
        }

        if (matchAny(FUN)) {
            Token funTok = prevTok();
            consumeTok(LEFT_PAREN, "Expected '(' after keyword 'fun' for anon function");
            List<Token> formals = new ArrayList<>();
            while (matchAny(IDENTIFIER)) {
                Token parameter = prevTok();
                formals.add(parameter);
                if (peekTok().type == RIGHT_PAREN) {
                    break;
                } else {
                    consumeTok(COMMA, "Expected ',' in anon function parameter list.");
                }
            }
            consumeTok(RIGHT_PAREN, "Expected ')' to end anon function parameter list.");
            Stmt body = null;
            if (matchAny(LEFT_BRACE)) {
                body = new Stmt.Block(blockStmts());
            } else {
                consumeTok(LEFT_BRACE, "Expected { after anon function parameter list");
            }
            Expr.AnonFn anonFn = new Expr.AnonFn(funTok, formals, body);
            return anonFn;
        }

        throw error(peekTok(), "Expected expression");
    }

    private boolean matchAny(TokenType... ttypes) {
        for (TokenType ttype : ttypes) {
            if (checkTok(ttype)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consumeTok(TokenType ttype, String msg) {
        if (checkTok(ttype)) {
            advance();
            return prevTok();
        } else {
            throw error(peekTok(), msg);
        }
    }

    private boolean checkTok(TokenType ttype) {
        if (isAtEnd()) return false;
        return peekTok().type == ttype;
    }

    private Token peekTok() {
        return tokens.get(current);
    }

    private Token peekTokN(int n) {
        if (isAtEnd()) return null;
        return tokens.get(current+n);
    }

    private Token prevTok() {
        if (current == 0) return peekTok();
        return tokens.get(current-1);
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return prevTok();
    }

    private boolean isAtEnd() {
        return peekTok().type == EOF;
    }

    private ParseError error(Token token, String msg) {
        Lox.error(token, msg);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (prevTok().type == SEMICOLON) return;

            switch (peekTok().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }
}
