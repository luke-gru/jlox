package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.craftinginterpreters.lox.TokenType.*;

class Scanner {
    private String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    public int line = 1;
    public int inBlock = 0;
    private boolean scriptEnded = false; // ended with __END__ keyword

    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and",    AND);
        keywords.put("class",  CLASS);
        keywords.put("module", MODULE);
        keywords.put("else",   ELSE);
        keywords.put("false",  FALSE);
        keywords.put("for",    FOR);
        keywords.put("foreach", FOREACH);
        keywords.put("in",     IN);
        keywords.put("fun",    FUN);
        keywords.put("if",     IF);
        keywords.put("nil",    NIL);
        keywords.put("or",     OR);
        keywords.put("print",  PRINT);
        keywords.put("return", RETURN);
        keywords.put("super",  SUPER);
        keywords.put("this",   THIS);
        keywords.put("true",   TRUE);
        keywords.put("var",    VAR);
        keywords.put("while",  WHILE);
        keywords.put("break",  BREAK);
        keywords.put("continue",  CONTINUE);
        keywords.put("try",    TRY);
        keywords.put("catch",  CATCH);
        keywords.put("throw",  THROW);
        keywords.put("__END__",  END_SCRIPT);
    }

    Scanner(String source) {
        this.source = source;
    }

    public void reset() {
        this.start = 0;
        this.current = 0;
        this.line = 1;
        this.inBlock = 0;
        this.tokens.clear();
    }

    public List<Token> scanTokens() {
        scanUntilEnd();
        addEOF();
        return tokens;
    }

    public List<Token> scanUntilEnd() {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current;
            scanToken();
        }
        return tokens;
    }

    public void appendSrc(String src) {
        this.source += src;
    }

    public void addEOF() {
        tokens.add(new Token(EOF, "", null, line));
    }

    private boolean isAtEnd() {
        return this.scriptEnded || current >= source.length();
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); inBlock++; break;
            case '}': addToken(RIGHT_BRACE); inBlock--; break;
            case '[': addToken(LEFT_BRACKET); break;
            case ']': addToken(RIGHT_BRACKET); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case ';': addToken(SEMICOLON); break;
            case ':': addToken(COLON); break;
            case '*': {
                if (match('=')) {
                    addToken(STAR_EQUAL);
                } else {
                    addToken(STAR);
                }
                break;
            }
            case '-': {
                if (match('=')) {
                    addToken(MINUS_EQUAL);
                } else {
                    addToken(MINUS);
                }
                break;
            }
            case '+': {
                if (match('=')) {
                    addToken(PLUS_EQUAL);
                } else {
                    addToken(PLUS);
                }
                break;
            }
            case '!': addToken(match('=') ? BANG_EQUAL : BANG); break;
            case '=': addToken(match('=') ? EQUAL_EQUAL : EQUAL); break;
            case '<': addToken(match('=') ? LESS_EQUAL : LESS); break;
            case '>': addToken(match('=') ? GREATER_EQUAL : GREATER); break;
            case '&': {
                if (match('&')) {
                    addToken(AND); break;
                }
                // fallthru
            }
            case '|': {
                if (match('|')) {
                    addToken(OR); break;
                }
                // fallthru
            }
            case '/': {
                if (match('/')) { // single-line comment (// ...)
                    // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else if (match('*')) { // multi-line comment (/* ... */)
                    char ch;
                    boolean seenStar = false;
                    while (!isAtEnd()) {
                        ch = peek();
                        if (seenStar && ch == '/') {
                            advance();
                            break;
                        }
                        if (ch == '\n') {
                            line++;
                        }
                        seenStar = (ch == '*');
                        advance();
                    }
                    break;
                } else {
                    if (match('=')) {
                        addToken(SLASH_EQUAL);
                    } else {
                        addToken(SLASH);
                    }
                }
                break;
            }
            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace.
                break;

            case '\n':
                line++;
                break;
            case '"': doubleQuotedString(false); break;
            case '\'': singleQuotedString(false); break;
            default:
                if (c == 's' && peek() == '"') {
                    advance();
                    start += 1;
                    doubleQuotedString(true);
                    return;
                } else if (c == 's' && peek() == '\'') {
                    advance();
                    start += 1;
                    singleQuotedString(true);
                    return;
                }
                if (LoxUtil.isDigit(c)) {
                    number();
                } else if (LoxUtil.isAlpha(c)) {
                    identifier();
                } else {
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    private char advance() {
        current++;
        return source.charAt(current-1);
    }


    private void identifier() {
        while (LoxUtil.isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);
        if (text.equals("__LINE__")) {
            addToken(NUMBER, (double)this.line);
            return;
        }

        TokenType ttype = keywords.get(text);
        if (ttype == null) {
            ttype = IDENTIFIER;
        } else if (ttype == END_SCRIPT) {
            this.scriptEnded = true;
            addToken(EOF);
            return;
        }
        addToken(ttype);
    }

    private void doubleQuotedString(boolean isStaticString) {
        while ((peek() != '"' || peekPrev() == '\\') && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        // Unterminated string.
        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // The closing ".
        advance();

        // Trim the surrounding quotes.
        String value = source.substring(start + 1, current - 1);
        value = value.replaceAll("\\\\\"", "\""); // replace \" (escaped dquote) with " for the lox string
        value = value.replaceAll("\\\\n", "\n"); // replace \n (escaped newline) with \n for the lox string
        value = value.replaceAll("\\\\t", "\t"); // replace \t (escaped newline) with \t for the lox string
        value = value.replaceAll("\\\\r", "\r"); // replace \r (escaped newline) with \r for the lox string
        if (isStaticString) {
            addToken(ST_STRING, value);
        } else {
            addToken(DQ_STRING, value);
        }
    }

    private void singleQuotedString(boolean isStaticString) {
        while ((peek() != '\'' || peekPrev() == '\\') && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        // Unterminated string.
        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // The closing '.
        advance();

        // Trim the surrounding quotes.
        String value = source.substring(start + 1, current - 1);
        value = value.replaceAll("\\\\'", "'"); // replace \' (escaped squote) with ' for the lox string
        if (isStaticString) {
            addToken(ST_STRING, value);
        } else {
            addToken(SQ_STRING, value);
        }
    }

    private void number() {
        while (LoxUtil.isDigit(peek())) advance();

        // Look for a fractional part.
        if (peek() == '.' && LoxUtil.isDigit(peekNext())) {
            // Consume the "."
            advance();

            while (LoxUtil.isDigit(peek())) advance();
        }

        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    private void addToken(TokenType ttype) {
        addToken(ttype, null);
    }

    private void addToken(TokenType ttype, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(ttype, text, literal, line));
    }

    private char peek() {
        if (isAtEnd()) {
            return '\0';
        } else {
            return source.charAt(current);
        }
    }

    private char peekNext() {
        if ((current + 1) >= source.length()) {
            return '\0';
        } else {
            return source.charAt(current+1);
        }
    }

    private char peekPrev() {
        if ((current-1) >= 0) {
            return source.charAt(current-1);
        } else {
            return '\0';
        }
    }

    private boolean match(char c) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != c) return false;
        current++;
        return true;
    }

}
