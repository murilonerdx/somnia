package somnia.lang

class Lexer(private val input: String) {
    private var pos = 0
    private var line = 1
    private var col = 1

    private val keywords = mapOf(
        "ego" to TokenType.KEYWORD_EGO, "act" to TokenType.KEYWORD_ACT,
        "drive" to TokenType.KEYWORD_DRIVE, "affect" to TokenType.KEYWORD_AFFECT,
        "trace" to TokenType.KEYWORD_TRACE, "archetype" to TokenType.KEYWORD_ARCHETYPE,
        "when" to TokenType.KEYWORD_WHEN, "propose" to TokenType.KEYWORD_PROPOSE,
        "forbid" to TokenType.KEYWORD_FORBID, "require" to TokenType.KEYWORD_REQUIRE,
        "forbid" to TokenType.KEYWORD_FORBID, "require" to TokenType.KEYWORD_REQUIRE,
        "const" to TokenType.KEYWORD_CONST, "let" to TokenType.KEYWORD_LET,
        "if" to TokenType.KEYWORD_IF, "else" to TokenType.KEYWORD_ELSE, "return" to TokenType.KEYWORD_RETURN,
        "select" to TokenType.KEYWORD_SELECT, "beam" to TokenType.KEYWORD_BEAM,
        "top" to TokenType.KEYWORD_TOP, "sample" to TokenType.KEYWORD_SAMPLE,
        "intent" to TokenType.KEYWORD_INTENT, "action" to TokenType.KEYWORD_ACTION,
        "run" to TokenType.KEYWORD_RUN, "expose" to TokenType.KEYWORD_EXPOSE,
        "as" to TokenType.KEYWORD_AS, "import" to TokenType.KEYWORD_IMPORT,
        "and" to TokenType.KEYWORD_AND, "or" to TokenType.KEYWORD_OR,
        "not" to TokenType.KEYWORD_NOT, "where" to TokenType.KEYWORD_WHERE,
        
        // Vertical 1
        "app" to TokenType.KEYWORD_APP, "package" to TokenType.KEYWORD_PACKAGE, "spring" to TokenType.KEYWORD_SPRING, "boot" to TokenType.KEYWORD_BOOT,
        "config" to TokenType.KEYWORD_CONFIG, "prefix" to TokenType.KEYWORD_PREFIX,
        "dto" to TokenType.KEYWORD_DTO,
        "entity" to TokenType.KEYWORD_ENTITY, "table" to TokenType.KEYWORD_TABLE,
        "repository" to TokenType.KEYWORD_REPOSITORY, "jpa" to TokenType.KEYWORD_JPA, "fun" to TokenType.KEYWORD_FUN,
        "errors" to TokenType.KEYWORD_ERRORS, "map" to TokenType.KEYWORD_MAP, "code" to TokenType.KEYWORD_CODE,
        "security" to TokenType.KEYWORD_SECURITY, "jwt" to TokenType.KEYWORD_JWT, "rules" to TokenType.KEYWORD_RULES, 
        "allow" to TokenType.KEYWORD_ALLOW, "deny" to TokenType.KEYWORD_DENY,
        "permissions" to TokenType.KEYWORD_PERMISSIONS, "permission" to TokenType.KEYWORD_PERMISSION, 
        "requires" to TokenType.KEYWORD_REQUIRES, "roles" to TokenType.KEYWORD_ROLES,
        "http" to TokenType.KEYWORD_HTTP, "base" to TokenType.KEYWORD_BASE, "on" to TokenType.KEYWORD_ON, 
        "GET" to TokenType.KEYWORD_GET, "POST" to TokenType.KEYWORD_POST, "PUT" to TokenType.KEYWORD_PUT, "DELETE" to TokenType.KEYWORD_DELETE, "PATCH" to TokenType.KEYWORD_PATCH,
        "args" to TokenType.KEYWORD_ARGS, "body" to TokenType.KEYWORD_BODY, "returns" to TokenType.KEYWORD_RETURNS, 
        "status" to TokenType.KEYWORD_STATUS, "auth" to TokenType.KEYWORD_AUTH, "valid" to TokenType.KEYWORD_VALID,
        "bind" to TokenType.KEYWORD_BIND, "repo" to TokenType.KEYWORD_REPO, "fail" to TokenType.KEYWORD_FAIL, 
        "then" to TokenType.KEYWORD_THEN, "failOk" to TokenType.KEYWORD_FAILOK,
        "sql" to TokenType.KEYWORD_SQL, "tx" to TokenType.KEYWORD_TX, "jpaSpec" to TokenType.KEYWORD_JPASPEC,
        "query" to TokenType.KEYWORD_QUERY, "redis" to TokenType.KEYWORD_REDIS, "required" to TokenType.KEYWORD_REQUIRED,
        "render" to TokenType.KEYWORD_RENDER, "using" to TokenType.KEYWORD_USING, "result" to TokenType.KEYWORD_RESULT,
        "from" to TokenType.KEYWORD_FROM,
        // Modules
        "kafka" to TokenType.KEYWORD_KAFKA,
        "topic" to TokenType.KEYWORD_TOPIC,
        "brokers" to TokenType.KEYWORD_BROKERS,
        "consumerGroup" to TokenType.KEYWORD_CONSUMERGROUP,
        "deadLetter" to TokenType.KEYWORD_DEADLETTER,
        "httpClient" to TokenType.KEYWORD_HTTPCLIENT,
        "baseUrl" to TokenType.KEYWORD_BASEURL,
        "auth" to TokenType.KEYWORD_AUTH,
        "bearer" to TokenType.KEYWORD_BEARER,
        "timeout" to TokenType.KEYWORD_TIMEOUT,
        "retry" to TokenType.KEYWORD_RETRY,
        "times" to TokenType.KEYWORD_TIMES,
        "backoff" to TokenType.KEYWORD_BACKOFF,
        "ms" to TokenType.KEYWORD_MS,
        "key" to TokenType.KEYWORD_KEY,
        "value" to TokenType.KEYWORD_VALUE,
        "publish" to TokenType.KEYWORD_PUBLISH,
        "client" to TokenType.KEYWORD_CLIENT,
        "json" to TokenType.KEYWORD_JSON,
        "client" to TokenType.KEYWORD_CLIENT,
        "json" to TokenType.KEYWORD_JSON,
        "pattern" to TokenType.KEYWORD_PATTERN,
        "contract" to TokenType.KEYWORD_CONTRACT,
        "implements" to TokenType.KEYWORD_IMPLEMENTS
    )

    fun tokenize(): List<Token> = tokenizeWithSpans().map { it.token }

    fun tokenizeWithSpans(): List<PositionedToken> {
        val tokens = mutableListOf<PositionedToken>()
        while (pos < input.length) {
            val c = peek()
            when (c) {
                ' ', '\t', '\r' -> advance()
                '\n' -> { line++; col = 1; pos++ }
                '+' -> tokens.add(singleChar(TokenType.PLUS, "+"))
                '-' -> tokens.add(singleChar(TokenType.MINUS, "-"))
                '*' -> tokens.add(singleChar(TokenType.STAR, "*"))
                '@' -> tokens.add(singleChar(TokenType.AT, "@"))
                '.' -> tokens.add(singleChar(TokenType.DOT, "."))
                ':' -> tokens.add(singleChar(TokenType.COLON, ":"))
                '[' -> tokens.add(singleChar(TokenType.LBRACKET, "["))
                ']' -> tokens.add(singleChar(TokenType.RBRACKET, "]"))
                '(' -> tokens.add(singleChar(TokenType.LPAREN, "("))
                ')' -> tokens.add(singleChar(TokenType.RPAREN, ")"))
                '{' -> tokens.add(singleChar(TokenType.LBRACE, "{"))
                '}' -> tokens.add(singleChar(TokenType.RBRACE, "}"))
                ',' -> tokens.add(singleChar(TokenType.COMMA, ","))
                '#' -> skipComment()    // Python-style comments
                '/' -> {
                    if (peekNext() == '/') skipComment() else tokens.add(singleChar(TokenType.SLASH, "/"))
                }
                '<' -> {
                    if (peekNext() == '=') tokens.add(fixed2(TokenType.LT_EQ, "<="))
                    else tokens.add(singleChar(TokenType.LT, "<"))
                }
                '>' -> {
                    if (peekNext() == '=') tokens.add(fixed2(TokenType.GT_EQ, ">="))
                    else tokens.add(singleChar(TokenType.GT, ">"))
                }
                '!' -> {
                    if (peekNext() == '=') tokens.add(fixed2(TokenType.BANG_EQ, "!="))
                    else tokens.add(singleChar(TokenType.KEYWORD_NOT, "!"))
                }
                '=' -> {
                    if (peekNext() == '>') tokens.add(fixed2(TokenType.FAT_ARROW, "=>"))
                    else if (peekNext() == '=') tokens.add(fixed2(TokenType.EQ_EQ, "=="))
                    else tokens.add(singleChar(TokenType.EQ, "="))
                }
                '"' -> tokens.add(readString())
                else -> {
                    when {
                        c.isDigit() -> tokens.add(readNumber())
                        isAlpha(c) -> tokens.add(readIdentifier())
                        else -> error("Unexpected character: $c at line $line")
                    }
                }
            }
        }
        tokens.add(PositionedToken(Token(TokenType.EOF, "", line), Span(line, col, line, col, pos, pos)))
        return tokens
    }

    private fun peek(offset: Int = 0): Char {
        if (pos + offset >= input.length) return '\u0000'
        return input[pos + offset]
    }

    private fun peekNext() = peek(1)

    private fun advance() { pos++; col++ }

    private fun singleChar(type: TokenType, lit: String): PositionedToken {
        val startPos = pos
        val startLine = line
        val startCol = col
        advance()
        return PositionedToken(
            Token(type, lit, startLine),
            Span(startLine, startCol, line, col, startPos, pos)
        )
    }

    private fun fixed2(type: TokenType, lit: String): PositionedToken {
        val startPos = pos
        val startLine = line
        val startCol = col
        advance(); advance()
        return PositionedToken(
            Token(type, lit, startLine),
            Span(startLine, startCol, line, col, startPos, pos)
        )
    }

    private fun skipComment() {
        while (peek() != '\n' && peek() != '\u0000') advance()
    }

    private fun readString(): PositionedToken {
        val startPos = pos
        val startLine = line
        val startCol = col

        advance() // skip opening "
        val contentStart = pos

        while (peek() != '"' && peek() != '\u0000') {
            if (peek() == '\n') {
                line++; col = 1; pos++
            } else {
                advance()
            }
        }
        if (peek() != '"') error("Unterminated string at line $line")

        val value = input.substring(contentStart, pos)
        advance() // closing "

        return PositionedToken(
            Token(TokenType.STRING_LITERAL, value, startLine),
            Span(startLine, startCol, line, col, startPos, pos)
        )
    }

    private fun readNumber(): PositionedToken {
        val startPos = pos
        val startLine = line
        val startCol = col

        while (peek().isDigit() || peek() == '.') advance()
        val value = input.substring(startPos, pos)
        return PositionedToken(
            Token(TokenType.NUMBER, value, startLine),
            Span(startLine, startCol, line, col, startPos, pos)
        )
    }

    private fun readIdentifier(): PositionedToken {
        val startPos = pos
        val startLine = line
        val startCol = col

        while (isAlphaNumeric(peek()) || peek() == '_') advance()
        val text = input.substring(startPos, pos)
        val type = keywords[text] ?: TokenType.IDENTIFIER
        return PositionedToken(
            Token(type, text, startLine),
            Span(startLine, startCol, line, col, startPos, pos)
        )
    }

    private fun isAlpha(c: Char) = c.isLetter() || c == '_'
    private fun isAlphaNumeric(c: Char) = isAlpha(c) || c.isDigit()
}
