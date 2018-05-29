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
    private int line = 1;
    public int inBlock = 0;
    private boolean scriptEnded = false; // ended with __END__ keyword

    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and",    AND);
        keywords.put("class",  CLASS);
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

    void reset() {
        this.start = 0;
        this.current = 0;
        this.line = 1;
        this.inBlock = 0;
        this.tokens.clear();
    }

    List<Token> scanTokens() {
        scanUntilEnd();
        addEOF();
        return tokens;
    }

    List<Token> scanUntilEnd() {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current;
            scanToken();
        }
        return tokens;
    }

    void appendSrc(String src) {
        this.source = source + src;
    }

    void addEOF() {
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
            case '/': {
                if (match('/')) {
                    // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
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
            case '"': doubleQuotedString(); break;
            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
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

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
            (c >= 'A' && c <= 'Z') ||
            c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();
        String text = source.substring(start, current);

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

    private void doubleQuotedString() {
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
        addToken(STRING, value);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void number() {
        while (isDigit(peek())) advance();

        // Look for a fractional part.
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the "."
            advance();

            while (isDigit(peek())) advance();
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
