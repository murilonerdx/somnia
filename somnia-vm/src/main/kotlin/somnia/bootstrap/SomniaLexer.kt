package somnia.bootstrap

/**
 * Token types for the Somnia lexer
 */
enum class TokenType {
    // Literals
    NUMBER, STRING, IDENTIFIER,
    
    // Keywords
    VAR, FUN, CLASS, METHOD, FIELD, EXTEND,
    IF, ELSE, WHEN, DEFAULT, WHILE, FOR, IN, RETURN,
    AND, OR, NOT, TRUE, FALSE, NULL,
    IMPORT, EXPORT, FROM, AS, TYPE, CONST,
    TEST, ASSERT, TRY, CATCH, NATIVE, DELETE,
    BOOL, LIST, MAP,
    
    // Operators
    PLUS, MINUS, STAR, SLASH, PERCENT, CARET,
    EQ, NE, LT, GT, LE, GE,
    ASSIGN, ARROW, FAT_ARROW,
    
    // Punctuation
    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
    COMMA, DOT, COLON, SEMICOLON, QUESTION,
    
    // Special
    NEWLINE, EOF, ERROR
}

data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any?,
    val line: Int
)

/**
 * Lexer for Somnia source code
 */
class SomniaLexer(private val source: String) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1
    
    private val keywords = mapOf(
        "var" to TokenType.VAR,
        "fun" to TokenType.FUN,
        "class" to TokenType.CLASS,
        "method" to TokenType.METHOD,
        "field" to TokenType.FIELD,
        "extend" to TokenType.EXTEND,
        "if" to TokenType.IF,
        "else" to TokenType.ELSE,
        "when" to TokenType.WHEN,
        "default" to TokenType.DEFAULT,
        "while" to TokenType.WHILE,
        "for" to TokenType.FOR,
        "in" to TokenType.IN,
        "return" to TokenType.RETURN,
        "and" to TokenType.AND,
        "or" to TokenType.OR,
        "not" to TokenType.NOT,
        "true" to TokenType.TRUE,
        "false" to TokenType.FALSE,
        "null" to TokenType.NULL,
        "import" to TokenType.IMPORT,
        "export" to TokenType.EXPORT,
        "from" to TokenType.FROM,
        "as" to TokenType.AS,
        "type" to TokenType.TYPE,
        "const" to TokenType.CONST,
        "test" to TokenType.TEST,
        "assert" to TokenType.ASSERT,
        "try" to TokenType.TRY,
        "catch" to TokenType.CATCH,
        "native" to TokenType.NATIVE,
        "delete" to TokenType.DELETE,
        "bool" to TokenType.BOOL,
        "list" to TokenType.LIST,
        "map" to TokenType.MAP
    )
    
    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }
        tokens.add(Token(TokenType.EOF, "", null, line))
        return tokens
    }
    
    private fun scanToken() {
        when (val c = advance()) {
            '(' -> addToken(TokenType.LPAREN)
            ')' -> addToken(TokenType.RPAREN)
            '{' -> addToken(TokenType.LBRACE)
            '}' -> addToken(TokenType.RBRACE)
            '[' -> addToken(TokenType.LBRACKET)
            ']' -> addToken(TokenType.RBRACKET)
            ',' -> addToken(TokenType.COMMA)
            '.' -> addToken(TokenType.DOT)
            ':' -> addToken(TokenType.COLON)
            ';' -> addToken(TokenType.SEMICOLON)
            '?' -> addToken(TokenType.QUESTION)
            '+' -> addToken(TokenType.PLUS)
            '*' -> addToken(TokenType.STAR)
            '/' -> addToken(TokenType.SLASH)
            '%' -> addToken(TokenType.PERCENT)
            '^' -> addToken(TokenType.CARET)
            '-' -> addToken(if (match('>')) TokenType.ARROW else TokenType.MINUS)
            '=' -> addToken(when {
                match('>') -> TokenType.FAT_ARROW
                match('=') -> TokenType.EQ
                else -> TokenType.ASSIGN
            })
            '!' -> addToken(if (match('=')) TokenType.NE else TokenType.ERROR)
            '<' -> addToken(if (match('=')) TokenType.LE else TokenType.LT)
            '>' -> addToken(if (match('=')) TokenType.GE else TokenType.GT)
            '#' -> {
                // Comment
                while (peek() != '\n' && !isAtEnd()) advance()
            }
            ' ', '\r', '\t' -> { /* Ignore whitespace */ }
            '\n' -> {
                line++
                // addToken(TokenType.NEWLINE)
            }
            '"' -> string()
            else -> when {
                c.isDigit() -> number()
                c.isLetter() || c == '_' -> identifier()
                else -> { /* Unknown character */ }
            }
        }
    }
    
    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            if (peek() == '\\' && peekNext() == '"') {
                advance() // Skip backslash
            }
            advance()
        }
        
        if (isAtEnd()) {
            // Unterminated string
            return
        }
        
        advance() // Closing quote
        
        val value = source.substring(start + 1, current - 1)
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
        addToken(TokenType.STRING, value)
    }
    
    private fun number() {
        while (peek().isDigit()) advance()
        
        if (peek() == '.' && peekNext().isDigit()) {
            advance() // Consume '.'
            while (peek().isDigit()) advance()
        }
        
        val value = source.substring(start, current).toDouble()
        addToken(TokenType.NUMBER, value)
    }
    
    private fun identifier() {
        while (peek().isLetterOrDigit() || peek() == '_') advance()
        
        val text = source.substring(start, current)
        val type = keywords[text] ?: TokenType.IDENTIFIER
        addToken(type)
    }
    
    private fun isAtEnd() = current >= source.length
    private fun advance() = source[current++]
    private fun peek() = if (isAtEnd()) '\u0000' else source[current]
    private fun peekNext() = if (current + 1 >= source.length) '\u0000' else source[current + 1]
    
    private fun match(expected: Char): Boolean {
        if (isAtEnd() || source[current] != expected) return false
        current++
        return true
    }
    
    private fun addToken(type: TokenType, literal: Any? = null) {
        tokens.add(Token(type, source.substring(start, current), literal, line))
    }
}
