package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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
 *                | "var" IDENTIFIER ( "," IDENTIFIER )+ ( "= " expression )? * ";"
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
 *                | tryStmt
 *                | throwStmt
 *                | continueStmt
 *                | breakStmt ;
 *
 * exprStmt       : expression ";" ;
 * printStmt      : "print" expression ";" ;
 * blockStmt      : "{" declaration* "}"
 * ifStmt         : "if" "(" expression ")" statement ( "else" statement )? ;
 * whileStmt      : "while" "(" expression ")" statement ;
 * forStmt        : "for" "(" (varDecl | exprStmt | ";") expression? ";" expression? ")" statement ;
 * tryStmt        : "try" blockStmt ( "catch" "(" expression IDENTIFIER? ")" blockStmt )*
 * throwStmt      : "throw" expression ";" ;
 * continueStmt   : "continue" ";" ;
 * breakStmt      : "break" ";" ;
 * expression     : assignment ;
 * assignment     : ( (call ".") | (call "[" expression "]"))? IDENTIFIER "=" assignment
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
 * expressionList : (expression ",")+
 * primary        : NUMBER | STRING | "false" | "true" | "nil" | "this" | "super" "." IDENTIFIER
                  | IDENTIFIER | anonFn | arrayLiteral | "(" expression ")" ;
 * arrayLiteral   : "[" expressionList? "]"
 * anonFn         : "fun" "(" parameterList? ")" blockStmt ;
 */

public class Parser {
    public static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;
    private ParseError lastError = null;
    private Stmt inLoopStmt = null;
    private Stmt.Function currentFn = null;
    public Map<String, Stmt.Class> classMap = new HashMap<>();
    public Map<String, Stmt.Module> modMap = new HashMap<>();
    private List<String> nativeClassNames = new ArrayList<>();
    private Stmt.Class currentClass = null;
    private Stmt.Module currentMod = null;

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

    public void setNativeClassNames(List<String> classNames) {
        this.nativeClassNames = classNames;
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
                Token superName = null;
                Expr.Variable superNameVar = null;

                if (matchAny(LESS)) {
                    superName = consumeTok(IDENTIFIER, "Expected identifier after '<'");
                    superNameVar = new Expr.Variable(superName);
                }
                consumeTok(LEFT_BRACE, "Expected '{' after class name");
                Stmt.Class enclosingClass = this.currentClass;
                Stmt.Class classStmt = new Stmt.Class(name, superNameVar, null, null); // body set below
                this.currentClass = classStmt;
                List<Stmt> classBody = classBody();
                classStmt.body = classBody;
                classMap.put(name.lexeme, classStmt);
                this.currentClass = enclosingClass;
                return classStmt;
            }
            if (matchAny(MODULE)) {
                Token name = consumeTok(IDENTIFIER, "Expected identifier after 'module' keyword");
                consumeTok(LEFT_BRACE, "Expected '{' after module name");
                Stmt.Module enclosingMod = this.currentMod;
                Stmt.Module modStmt = new Stmt.Module(name, null); // body set below
                this.currentMod = modStmt;
                List<Stmt> modBody = classBody();
                modStmt.body = modBody;
                modMap.put(name.lexeme, modStmt);
                this.currentMod = enclosingMod;
                return modStmt;
            }
            return statement();
        } catch (ParseError error) {
            lastError = error;
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        List<Token> names = new ArrayList<Token>();
        List<String> nameStrs = new ArrayList<String>();
        boolean expectNewName = true;
        while (expectNewName && matchAny(IDENTIFIER)) {
            Token name = prevTok();
            names.add(name);
            nameStrs.add(name.lexeme);
            expectNewName = matchAny(COMMA);
        }
        if (expectNewName) {
            consumeTok(IDENTIFIER, "Expected variable name after 'var' keyword.");
        }
        // check that we don't shadow function parameters
        if (this.currentFn != null) {
            for (Param param : this.currentFn.formals) {
                if (nameStrs.contains(param.varName())) {
                    int index = nameStrs.indexOf(param.varName());
                    Token nameTok = names.get(index);
                    throw error(
                        nameTok,
                        "'var' assignment error: can't shadow parameter '" + param.varName() + "' in function <" +
                        this.currentFn.name.lexeme + ">"
                    );
                }
            }
        }

        List<Expr> initializers = new ArrayList<>();
        Expr initializer;
        int numInits = 0;
        if (matchAny(EQUAL)) {
            initializer = expression();
            initializers.add(initializer);
            numInits++;
            while (numInits < names.size() && matchAny(COMMA)) {
                initializer = expression();
                initializers.add(initializer);
                numInits++;
            }
        }

        if (initializers.size() > names.size()) {
            throw error(names.get(0), "too many initializer expressions in var statement");
        }

        consumeTok(SEMICOLON, "Expected ';' after variable declaration.");
        return new Stmt.Var(names, initializers);
    }

    private Stmt funDeclaration(FunctionType fnType) {
        Token name = consumeTok(IDENTIFIER, "Expected function name after 'fun' keyword.");
        consumeTok(LEFT_PAREN, "Expected '(' after function name.");
        List<Param> formals = new ArrayList<>();
        while (matchAny(IDENTIFIER, STAR)) {
            Token tok = prevTok();
            Token paramTok;
            boolean isSplat = false;
            boolean isKwarg = false;
            Expr defaultVal = null;
            if (tok.type == STAR) {
                paramTok = consumeTok(IDENTIFIER, "Expected parameter name in function parameter list after '*'.");
                isSplat = true;
            } else {
                paramTok = tok;
                if (matchAny(EQUAL)) { // default argument
                    defaultVal = expression();
                } else if (matchAny(COLON)) { // keyword argument
                    isKwarg = true;
                    if (peekTok().type == COMMA) {
                        defaultVal = null;
                    } else {
                        defaultVal = expression();
                    }
                }
            }
            Param paramObj = new Param(paramTok, defaultVal, isSplat, isKwarg);
            formals.add(paramObj);
            if (peekTok().type == RIGHT_PAREN) {
                break;
            } else {
                consumeTok(COMMA, "Expected ',' in function parameter list.");
            }
            if (isSplat) { // handle (arg1, *splat,) [with trailing comma]
                break;
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
            consumeTok(LEFT_BRACE, "Expected '{' after function parameter list");
        }
        return fnStmt;
    }

    private Stmt getterDeclaration() {
        Token name = consumeTok(IDENTIFIER, "Expected getter name in getter decl");
        consumeTok(LEFT_BRACE, "Expected '{' after getter name in getter decl");
        FunctionType fnType = FunctionType.GETTER;
        List<Param> params = new ArrayList<>();
        Stmt.Function fnStmt = new Stmt.Function(name, params, null, fnType, this.currentClass);
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
        List<Param> params = new ArrayList<>();
        consumeTok(LEFT_PAREN, "Expected '(' after '=' in setter decl");
        Token paramTok = consumeTok(IDENTIFIER, "Expected parameter in setter decl");
        params.add(new Param(paramTok, null, false, false));
        consumeTok(RIGHT_PAREN, "Expected ')' after parameter in setter decl");
        consumeTok(LEFT_BRACE, "Expected '{' after parameter in setter decl");
        Stmt.Function fnStmt = new Stmt.Function(name, params, null, fnType, this.currentClass);
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
            // getter
            } else if (checkTok(IDENTIFIER) && peekTokN(1).type == LEFT_BRACE) {
                Stmt stmt = getterDeclaration();
                body.add(stmt);
            // setter
            } else if (checkTok(IDENTIFIER) && peekTokN(1).type == EQUAL && peekTokN(2).type == LEFT_PAREN) {
                Stmt stmt = setterDeclaration();
                body.add(stmt);
            // end of class declaration
            } else if (checkTok(RIGHT_BRACE)) {
                break;
            // other statement
            } else {
                Stmt decl = declaration();
                body.add(decl);
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
        if (matchAny(FOR)) { // for (..;.. ;..) or (for var in obj)
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
        if (matchAny(FOREACH)) {
            consumeTok(LEFT_PAREN, "expected '(' after keyword 'foreach'");
            List<Token> variables = new ArrayList<>();
            while (matchAny(IDENTIFIER)) {
                Token ident = prevTok();
                variables.add(ident);
                if (matchAny(COMMA)) {
                    continue;
                } else {
                    break;
                }
            }
            consumeTok(IN, "expected keyword 'in' after keyword 'foreach'");
            Expr expr = expression();
            consumeTok(RIGHT_PAREN, "expected ')' to end 'foreach' statement");
            Stmt.Block body = (Stmt.Block)statement();
            return new Stmt.Foreach(variables, expr, body);
        }
        if (matchAny(IN)) {
            consumeTok(LEFT_PAREN, "expected '(' after keyword 'in'");
            Expr objectExpr = expression();
            consumeTok(RIGHT_PAREN, "Expected ')' to end 'in' expression");
            consumeTok(LEFT_BRACE, "Expected '{' to start 'in' block");
            List<Stmt> inBody = classBody();
            return new Stmt.In(objectExpr, inBody);
        }
        if (matchAny(TRY)) {
            consumeTok(LEFT_BRACE, "Expected '{' after keyword 'try'");
            List<Stmt> tryBlockStmts = blockStmts();
            Stmt.Block tryBlock = new Stmt.Block(tryBlockStmts);
            List<Stmt.Catch> catchStmts = new ArrayList<>();
            while (matchAny(CATCH)) {
                consumeTok(LEFT_PAREN, "Expected '(' after keyword 'catch'");
                Expr catchExpr = expression();
                Token identToken = null;
                if (matchAny(IDENTIFIER)) {
                    identToken = prevTok();
                }
                consumeTok(RIGHT_PAREN, "Expected ')' after 'catch' expression");
                consumeTok(LEFT_BRACE, "Expected '{' after 'catch' expression");
                Stmt.Block catchBlock = new Stmt.Block(blockStmts());
                Stmt.Catch catchStmt = new Stmt.Catch(catchExpr, identToken == null ? null : new Expr.Variable(identToken), catchBlock);
                catchStmts.add(catchStmt);
            }
            return new Stmt.Try(tryBlock, catchStmts);
        }
        if (matchAny(THROW)) {
            Token throwTok = prevTok();
            Expr throwExpr = expression();
            consumeTok(SEMICOLON, "Expected ';' after 'throw' statement");
            return new Stmt.Throw(throwTok, throwExpr);
        }
        if (matchAny(CONTINUE)) {
            Token contTok = prevTok();
            if (inLoopStmt == null) {
                throw error(peekTok(), "Keyword 'continue' can only be used 'while', 'for' or 'foreach' statements.");
            }
            consumeTok(SEMICOLON, "Expected ';' after keyword 'continue'");
            return new Stmt.Continue(contTok, inLoopStmt);
        }
        if (matchAny(BREAK)) {
            Token breakTok = prevTok();
            if (inLoopStmt == null) {
                throw error(peekTok(), "Keyword 'break' can only be used in 'while', 'for' or 'foreach' statements");
            }
            consumeTok(SEMICOLON, "Expected ';' after keyword 'break'");
            return new Stmt.Break(breakTok, inLoopStmt);
        }
        if (matchAny(RETURN)) {
            Token retTok = prevTok();
            Expr expression = null;
            if (matchAny(SEMICOLON)) {
                // do nothing: `return;`
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
        if (matchAny(EQUAL, PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL)) {
            Token tok = prevTok();
            Expr value = assignment();
            if (tok.type == PLUS_EQUAL) { // ex: a += 3 => a = a + 3
                tok.setType(PLUS, "+");
                value = new Expr.Binary(expr, tok, value);
            } else if (tok.type == MINUS_EQUAL) {
                tok.setType(MINUS, "-");
                value = new Expr.Binary(expr, tok, value);
            } else if (tok.type == STAR_EQUAL) {
                tok.setType(STAR, "*");
                value = new Expr.Binary(expr, tok, value);
            } else if (tok.type == SLASH_EQUAL) {
                tok.setType(SLASH, "/");
                value = new Expr.Binary(expr, tok, value);
            }
            if (expr instanceof Expr.PropAccess) {
                Expr.PropAccess propAccess = (Expr.PropAccess)expr;
                return new Expr.PropSet(propAccess.left, propAccess.property, value);
            } else if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Super) {
                Expr.Super superExpr = (Expr.Super)expr;
                return new Expr.PropSet(superExpr, superExpr.property, value);
            } else if (expr instanceof Expr.IndexedGet) {
                Expr.IndexedGet idxGet = (Expr.IndexedGet)expr;
                return new Expr.IndexedSet(idxGet.lbracket, idxGet.left, idxGet.indexExpr, value);
            }
            throw error(tok, "Invalid assignment target, must be variable, property name or array element");
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
                    while (true) {
                        Expr arg;
                        if (matchAny(STAR)) {
                            arg = expression();
                            arg = new Expr.SplatCall(arg);
                        } else {
                            if (peekTok().type == IDENTIFIER && peekTokN(1).type == COLON) {
                                Token kwTok = consumeTok(IDENTIFIER, "BUG");
                                consumeTok(COLON, "BUG");
                                Expr argExpr = expression();
                                arg = new Expr.KeywordArg(kwTok, argExpr);
                            } else {
                                arg = expression();
                            }
                        }
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
            } else if (checkTok(LEFT_BRACKET)) {
                advance();
                Token lbracket = prevTok();
                Expr indexExpr = expression();
                expr = new Expr.IndexedGet(lbracket, expr, indexExpr);
                consumeTok(RIGHT_BRACKET, "Expected ']' at end of index access expression");
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

        if (matchAny(NUMBER)) {
            return new Expr.Literal(prevTok().literal);
        }
        if (matchAny(SQ_STRING)) {
            return new Expr.Literal(
                new StringBuffer((String)prevTok().literal)
            );
        }
        if (matchAny(DQ_STRING)) {
            // parse out any string interpolation, "${expr}"
            String str = (String)prevTok().literal;
            Pattern p = Pattern.compile("\\$\\{.+?\\}");
            Matcher m = p.matcher(str);
            if (str.length() > 0 && m.find()) {
                int startIdx = m.start();
                int endIdx = m.end();
                int strlen = str.length();
                String strBefore = "";
                String strAfter = "";
                if (startIdx > 0) {
                    strBefore = str.substring(0, startIdx);
                }
                if (endIdx < (strlen-1)) {
                    strAfter = str.substring(endIdx, strlen-1);
                }
                String exprStr = str.substring(startIdx+2, endIdx-1);
                //System.err.println("interpolation found");
                //System.err.println("before: '" + strBefore + "'");
                //System.err.println("after: '" + strAfter + "'");
                //System.err.println("in: '" + exprStr + "'");
                Scanner exprScanner = new Scanner(exprStr);
                Token prevToken = prevTok();
                exprScanner.line = prevToken.line;
                List<Token> newTokens = exprScanner.scanUntilEnd();
                addTokens(newTokens, current);
                //System.err.println("peekTok() = " + peekTok().lexeme);
                //for (Token tok : this.tokens) {
                    //System.err.println("TOK = " + tok.lexeme);
                //}
                Expr innerExpr = expression();
                //System.err.println("after inner");
                // interpolation transformation: change
                //   "Welcome, ${person.name}!" to
                //   "Welcome, " + person.name + "!"
                Expr.Grouping group = new Expr.Grouping(null);
                Expr.Literal litBefore = new Expr.Literal(strBefore);
                Expr.Literal litAfter = new Expr.Literal(strAfter);
                Expr.Binary plusOp1 = new Expr.Binary(
                    litBefore,
                    new Token(PLUS, "+", null, prevToken.line),
                    innerExpr
                );
                Expr.Binary plusOp2 = new Expr.Binary(
                    plusOp1,
                    new Token(PLUS, "+", null, prevToken.line),
                    litAfter
                );
                group.expression = plusOp2;
                return group;
            }
            return new Expr.Literal(new StringBuffer(str));
        }
        // static string (var s = s"frozen, static string")
        if (matchAny(ST_STRING)) {
            return new Expr.Literal(
                (String)prevTok().literal
            );
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

        if (matchAny(LEFT_BRACKET)) {
            List<Expr> list = new ArrayList<>();
            Expr.Array ary = new Expr.Array(prevTok(), list);
            while (!checkTok(RIGHT_BRACKET)) {
                if (checkTok(COMMA) && peekTokN(1).type == RIGHT_BRACKET) {
                    advance();
                    break;
                }
                list.add(expression());
                if (checkTok(RIGHT_BRACKET)) {
                    break;
                }
                consumeTok(COMMA, "Expected ',' to separate expressions in Array literal");
            }
            consumeTok(RIGHT_BRACKET, "Expected ']' to end array expression");
            return ary;
        }

        if (matchAny(IDENTIFIER)) {
            return new Expr.Variable(prevTok());
        }

        if (matchAny(FUN)) {
            Token funTok = prevTok();
            consumeTok(LEFT_PAREN, "Expected '(' after keyword 'fun' for anonymous function");
            List<Param> params = new ArrayList<>();
            while (matchAny(IDENTIFIER, STAR)) {
                Token tok = prevTok();
                Token paramTok;
                boolean isSplat = false;
                boolean isKwarg = false;
                Expr defaultVal = null;
                if (tok.type == STAR) {
                    paramTok = consumeTok(IDENTIFIER, "Expected identifier after '*' in parameter list");
                    isSplat = true;
                } else {
                    paramTok = tok;
                    if (matchAny(EQUAL)) { // default argument
                        defaultVal = expression();
                    } else if (matchAny(COLON)) { // keyword argument
                        isKwarg = true;
                        if (peekTok().type == COMMA) {
                            defaultVal = null;
                        } else {
                            defaultVal = expression();
                        }
                    }
                }
                params.add(new Param(paramTok, defaultVal, isSplat, isKwarg));
                if (peekTok().type == RIGHT_PAREN) {
                    break;
                } else {
                    consumeTok(COMMA, "Expected ',' in anon function parameter list.");
                }
                if (isSplat) {
                    break;
                }
            }
            consumeTok(RIGHT_PAREN, "Expected ')' to end anon function parameter list.");
            Stmt body = null;
            if (matchAny(LEFT_BRACE)) {
                body = new Stmt.Block(blockStmts());
            } else {
                consumeTok(LEFT_BRACE, "Expected '{' after anonymous function's parameter list");
            }
            Expr.AnonFn anonFn = new Expr.AnonFn(funTok, params, body);
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

    private void addTokens(List<Token> newTokens, int idx) {
        tokens.addAll(idx, newTokens);
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
                case MODULE:
                case FUN:
                case VAR:
                case FOR:
                case FOREACH:
                case IN:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

    private boolean classExists(String name) {
        return classMap.containsKey(name) || nativeClassNames.contains(name);
    }

    private boolean nativeClassExists(String name) {
        return nativeClassNames.contains(name);
    }
}
