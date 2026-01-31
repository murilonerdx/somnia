package somnia.bootstrap

/**
 * AST Nodes for Somnia
 */
sealed class AstNode {
    abstract val line: Int
}

// Expressions
sealed class Expr : AstNode()

data class LiteralExpr(val value: Any?, override val line: Int) : Expr()
data class IdentifierExpr(val name: String, override val line: Int) : Expr()
data class BinaryExpr(val left: Expr, val op: String, val right: Expr, override val line: Int) : Expr()
data class UnaryExpr(val op: String, val operand: Expr, override val line: Int) : Expr()
data class CallExpr(val callee: Expr, val args: List<Expr>, override val line: Int) : Expr()
data class GetExpr(val obj: Expr, val name: String, override val line: Int) : Expr()
data class SetExpr(val obj: Expr, val name: String, val value: Expr, override val line: Int) : Expr()
data class IndexExpr(val obj: Expr, val index: Expr, override val line: Int) : Expr()
data class ListExpr(val items: List<Expr>, override val line: Int) : Expr()
data class MapExpr(val entries: List<Pair<String, Expr>>, override val line: Int) : Expr()
data class LambdaExpr(val params: List<String>, val body: List<Stmt>, override val line: Int) : Expr()
data class IfExpr(val condition: Expr, val thenBranch: Expr, val elseBranch: Expr?, override val line: Int) : Expr()
data class ObjectExpr(val className: String, val fields: Map<String, Expr>, override val line: Int) : Expr()

// Statements
sealed class Stmt : AstNode()

data class ExprStmt(val expr: Expr, override val line: Int) : Stmt()
data class VarStmt(val name: String, val initializer: Expr?, override val line: Int) : Stmt()
data class AssignStmt(val name: String, val value: Expr, override val line: Int) : Stmt()
data class BlockStmt(val statements: List<Stmt>, override val line: Int) : Stmt()
data class IfStmt(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?, override val line: Int) : Stmt()
data class WhileStmt(val condition: Expr, val body: Stmt, override val line: Int) : Stmt()
data class ForStmt(val name: String, val iterable: Expr, val body: Stmt, override val line: Int) : Stmt()
data class WhenStmt(val condition: Expr, val thenBranch: Stmt, override val line: Int) : Stmt()
data class ReturnStmt(val value: Expr?, override val line: Int) : Stmt()
data class FunStmt(val name: String, val params: List<String>, val body: List<Stmt>, override val line: Int) : Stmt()
data class ClassStmt(val name: String, val fields: List<Pair<String, Expr?>>, val methods: List<FunStmt>, override val line: Int) : Stmt()
data class ImportStmt(val path: String, override val line: Int) : Stmt()
data class ExportStmt(val names: List<String>, override val line: Int) : Stmt()
data class TestStmt(val name: String, val body: List<Stmt>, override val line: Int) : Stmt()
data class ConstStmt(val name: String, val value: Expr, override val line: Int) : Stmt()
data class TypeStmt(val name: String, val definition: String, override val line: Int) : Stmt()
data class ExtendStmt(val className: String, val methods: List<FunStmt>, override val line: Int) : Stmt()
data class TryStmt(val body: List<Stmt>, val catchVar: String?, val catchBody: List<Stmt>, override val line: Int) : Stmt()
data class AssertStmt(val expr: Expr, override val line: Int) : Stmt()
data class NativeFunStmt(val name: String, val params: List<String>, override val line: Int) : Stmt()

/**
 * Parser for Somnia
 */
class SomniaParser(private val tokens: List<Token>) {
    private var current = 0
    
    fun parse(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            try {
                declaration()?.let { statements.add(it) }
            } catch (e: Exception) {
                println("[PARSER ERROR] At line ${peek().line}: ${e.message}")
                synchronize()
            }
        }
        return statements
    }
    
    private fun declaration(): Stmt? {
        return when {
            match(TokenType.VAR) -> varDeclaration()
            match(TokenType.CONST) -> constDeclaration()
            match(TokenType.FUN) -> funDeclaration()
            match(TokenType.CLASS) -> classDeclaration()
            match(TokenType.IMPORT) -> importDeclaration()
            match(TokenType.EXPORT) -> exportDeclaration()
            match(TokenType.TEST) -> testDeclaration()
            match(TokenType.TYPE) -> typeDeclaration()
            match(TokenType.EXTEND) -> extendDeclaration()
            match(TokenType.NATIVE) -> nativeDeclaration()
            else -> statement()
        }
    }
    
    private fun varDeclaration(): Stmt {
        val name = consumeIdentifier("Expected variable name")
        val initializer = if (match(TokenType.ASSIGN)) expression() else null
        return VarStmt(name.lexeme, initializer, name.line)
    }
    
    private fun constDeclaration(): Stmt {
        val name = consumeIdentifier("Expected constant name")
        consume(TokenType.ASSIGN, "Expected '=' after constant name")
        val value = expression()
        return ConstStmt(name.lexeme, value, name.line)
    }
    
    private fun funDeclaration(): Stmt {
        val name = consumeIdentifier("Expected function name")
        consume(TokenType.LPAREN, "Expected '(' after function name")
        
        val params = mutableListOf<String>()
        if (!check(TokenType.RPAREN)) {
            do {
                val param = consumeIdentifier("Expected parameter name")
                params.add(param.lexeme)
                // Skip type annotation if present
                if (match(TokenType.COLON)) {
                    consumeIdentifier("Expected type")
                }
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "Expected ')' after parameters")
        
        // Skip return type
        if (match(TokenType.ARROW)) {
            consumeIdentifier("Expected return type")
        }
        
        consume(TokenType.LBRACE, "Expected '{' before function body")
        val body = block()
        
        return FunStmt(name.lexeme, params, body, name.line)
    }
    
    private fun classDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expected class name")
        consume(TokenType.LBRACE, "Expected '{' before class body")
        
        val fields = mutableListOf<Pair<String, Expr?>>()
        val methods = mutableListOf<FunStmt>()
        
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            when {
                match(TokenType.FIELD) -> {
                    val fieldName = consumeIdentifier("Expected field name")
                    // Skip type annotation
                    if (match(TokenType.COLON)) {
                        consumeIdentifier("Expected type")
                    }
                    val initializer = if (match(TokenType.ASSIGN)) expression() else null
                    fields.add(fieldName.lexeme to initializer)
                }
                match(TokenType.METHOD) -> {
                    val methodStmt = funDeclaration() as FunStmt
                    methods.add(methodStmt)
                }
                else -> {
                    advance() // Skip unknown token in class body
                }
            }
        }
        
        consume(TokenType.RBRACE, "Expected '}' after class body")
        return ClassStmt(name.lexeme, fields, methods, name.line)
    }
    
    private fun importDeclaration(): Stmt {
        val path = consume(TokenType.STRING, "Expected import path")
        return ImportStmt(path.literal as String, path.line)
    }
    
    private fun exportDeclaration(): Stmt {
        val names = mutableListOf<String>()
        
        if (match(TokenType.STAR)) {
            // export * from "module"
            consume(TokenType.FROM, "Expected 'from' after '*'")
            val path = consume(TokenType.STRING, "Expected module path")
            return ImportStmt(path.literal as String, path.line) // Treat as import for now
        }
        
        do {
            val name = consume(TokenType.IDENTIFIER, "Expected export name")
            names.add(name.lexeme)
        } while (match(TokenType.COMMA))
        
        return ExportStmt(names, previous().line)
    }
    
    private fun testDeclaration(): Stmt {
        val name = consume(TokenType.STRING, "Expected test name")
        consume(TokenType.LBRACE, "Expected '{' before test body")
        val body = block()
        return TestStmt(name.literal as String, body, name.line)
    }
    
    private fun typeDeclaration(): Stmt {
        val name = consumeIdentifier("Expected type name")
        consume(TokenType.ASSIGN, "Expected '=' after type name")
        val definition = consumeIdentifier("Expected type definition")
        return TypeStmt(name.lexeme, definition.lexeme, name.line)
    }
    
    private fun extendDeclaration(): Stmt {
        val className = consumeIdentifier("Expected class name to extend")
        consume(TokenType.LBRACE, "Expected '{' before extend body")
        
        val methods = mutableListOf<FunStmt>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            if (match(TokenType.METHOD)) {
                methods.add(funDeclaration() as FunStmt)
            } else {
                advance()
            }
        }
        consume(TokenType.RBRACE, "Expected '}' after extend body")
        return ExtendStmt(className.lexeme, methods, previous().line)
    }
    
    private fun nativeDeclaration(): Stmt {
        consume(TokenType.FUN, "Expected 'fun' after 'native'")
        val name = consumeIdentifier("Expected function name")
        consume(TokenType.LPAREN, "Expected '(' after function name")
        
        val params = mutableListOf<String>()
        if (!check(TokenType.RPAREN)) {
            do {
                val param = consume(TokenType.IDENTIFIER, "Expected parameter name")
                params.add(param.lexeme)
                if (match(TokenType.COLON)) consumeIdentifier("Expected type")
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "Expected ')' after parameters")
        
        if (match(TokenType.ARROW)) {
            consumeIdentifier("Expected return type")
        }
        
        return NativeFunStmt(name.lexeme, params, name.line)
    }
    
    private fun statement(): Stmt {
        return when {
            match(TokenType.IF) -> ifStatement()
            match(TokenType.WHEN) -> whenStatement()
            match(TokenType.DEFAULT) -> defaultStatement()
            match(TokenType.WHILE) -> whileStatement()
            match(TokenType.FOR) -> forStatement()
            match(TokenType.RETURN) -> returnStatement()
            match(TokenType.TRY) -> tryStatement()
            match(TokenType.ASSERT) -> assertStatement()
            match(TokenType.DELETE) -> deleteStatement()
            match(TokenType.LBRACE) -> BlockStmt(block(), previous().line)
            else -> expressionStatement()
        }
    }
    
    private fun ifStatement(): Stmt {
        val condition = expression()
        
        val thenBranch = if (match(TokenType.LBRACE)) {
            BlockStmt(block(), previous().line)
        } else {
            statement()
        }
        
        val elseBranch = if (match(TokenType.ELSE)) {
            if (match(TokenType.LBRACE)) {
                BlockStmt(block(), previous().line)
            } else {
                statement()
            }
        } else null
        
        return IfStmt(condition, thenBranch, elseBranch, previous().line)
    }
    
    private fun defaultStatement(): Stmt {
        // Skip '=>' if present
        match(TokenType.FAT_ARROW)
        
        val thenBranch = if (match(TokenType.LBRACE)) {
            BlockStmt(block(), previous().line)
        } else {
            statement()
        }
        
        // Treat as 'when true' or just a statement that always runs
        return WhenStmt(LiteralExpr(true, previous().line), thenBranch, previous().line)
    }
    
    private fun whenStatement(): Stmt {
        val condition = expression()
        
        // Skip '=>' if present
        match(TokenType.FAT_ARROW)
        
        val thenBranch = if (match(TokenType.LBRACE)) {
            BlockStmt(block(), previous().line)
        } else {
            statement()
        }
        
        return WhenStmt(condition, thenBranch, previous().line)
    }
    
    private fun whileStatement(): Stmt {
        val condition = expression()
        consume(TokenType.LBRACE, "Expected '{' before while body")
        val body = BlockStmt(block(), previous().line)
        return WhileStmt(condition, body, previous().line)
    }
    
    private fun forStatement(): Stmt {
        val name = consumeIdentifier("Expected variable name")
        consume(TokenType.IN, "Expected 'in' after variable")
        val iterable = expression()
        consume(TokenType.LBRACE, "Expected '{' before for body")
        val body = BlockStmt(block(), previous().line)
        return ForStmt(name.lexeme, iterable, body, previous().line)
    }
    
    private fun returnStatement(): Stmt {
        val value = if (!check(TokenType.RBRACE) && !check(TokenType.EOF) && !check(TokenType.ELSE)) {
            expression()
        } else null
        return ReturnStmt(value, previous().line)
    }
    
    private fun tryStatement(): Stmt {
        consume(TokenType.LBRACE, "Expected '{' after 'try'")
        val body = block()
        consume(TokenType.CATCH, "Expected 'catch' after 'try' block")
        var catchVar: String? = null
        if (check(TokenType.IDENTIFIER) || peek().type in listOf(TokenType.TYPE, TokenType.NULL, TokenType.STRING, TokenType.NUMBER, TokenType.BOOL, TokenType.LIST, TokenType.MAP, TokenType.NATIVE, TokenType.DEFAULT, TokenType.IN, TokenType.TEST, TokenType.ASSERT, TokenType.TRY, TokenType.CATCH, TokenType.DELETE, TokenType.VAR, TokenType.CONST, TokenType.IF, TokenType.ELSE, TokenType.WHILE, TokenType.FOR, TokenType.RETURN, TokenType.EXPORT, TokenType.IMPORT, TokenType.FROM, TokenType.AS, TokenType.FUN, TokenType.METHOD, TokenType.FIELD)) {
            catchVar = advance().lexeme
        }
        consume(TokenType.LBRACE, "Expected '{' before catch body")
        val catchBody = block()
        return TryStmt(body, catchVar, catchBody, previous().line)
    }
    
    private fun deleteStatement(): Stmt {
        val expr = expression()
        if (expr is IndexExpr) {
            // delete map[key]
            return ExprStmt(BinaryExpr(expr.obj, "delete", expr.index, expr.line), expr.line)
        }
        throw RuntimeException("Expected index expression after 'delete'")
    }
    
    private fun assertStatement(): Stmt {
        val expr = expression()
        return AssertStmt(expr, previous().line)
    }
    
    private fun expressionStatement(): Stmt {
        val expr = expression()
        
        // Check for assignment
        if (match(TokenType.ASSIGN)) {
            val value = expression()
            if (expr is IdentifierExpr) {
                return AssignStmt(expr.name, value, expr.line)
            }
            if (expr is GetExpr) {
                return ExprStmt(SetExpr(expr.obj, expr.name, value, expr.line), expr.line)
            }
        }
        
        return ExprStmt(expr, previous().line)
    }
    
    private fun block(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            declaration()?.let { statements.add(it) }
        }
        consume(TokenType.RBRACE, "Expected '}' after block")
        return statements
    }
    
    // Expression parsing with precedence
    private fun expression(): Expr = or()
    
    private fun or(): Expr {
        var expr = and()
        while (match(TokenType.OR)) {
            val right = and()
            expr = BinaryExpr(expr, "or", right, previous().line)
        }
        return expr
    }
    
    private fun and(): Expr {
        var expr = equality()
        while (match(TokenType.AND)) {
            val right = equality()
            expr = BinaryExpr(expr, "and", right, previous().line)
        }
        return expr
    }
    
    private fun equality(): Expr {
        var expr = comparison()
        while (match(TokenType.EQ, TokenType.NE)) {
            val op = previous().lexeme
            val right = comparison()
            expr = BinaryExpr(expr, op, right, previous().line)
        }
        return expr
    }
    
    private fun comparison(): Expr {
        var expr = term()
        while (match(TokenType.LT, TokenType.GT, TokenType.LE, TokenType.GE, TokenType.IN)) {
            val op = previous().lexeme
            val right = term()
            expr = BinaryExpr(expr, op, right, previous().line)
        }
        return expr
    }
    
    private fun term(): Expr {
        var expr = factor()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val op = previous().lexeme
            val right = factor()
            expr = BinaryExpr(expr, op, right, previous().line)
        }
        return expr
    }
    
    private fun factor(): Expr {
        var expr = unary()
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val op = previous().lexeme
            val right = unary()
            expr = BinaryExpr(expr, op, right, previous().line)
        }
        return expr
    }
    
    private fun unary(): Expr {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            val op = previous().lexeme
            val right = unary()
            return UnaryExpr(op, right, previous().line)
        }
        return call()
    }
    
    private fun call(): Expr {
        var expr = primary()
        
        while (true) {
            expr = when {
                match(TokenType.LPAREN) -> {
                    val args = mutableListOf<Expr>()
                    if (!check(TokenType.RPAREN)) {
                        do {
                            args.add(expression())
                        } while (match(TokenType.COMMA))
                    }
                    consume(TokenType.RPAREN, "Expected ')' after arguments")
                    CallExpr(expr, args, previous().line)
                }
                match(TokenType.DOT) -> {
                    val name = consumeIdentifier("Expected property name")
                    GetExpr(expr, name.lexeme, name.line)
                }
                match(TokenType.LBRACKET) -> {
                    val index = expression()
                    consume(TokenType.RBRACKET, "Expected ']' after index")
                    IndexExpr(expr, index, previous().line)
                }
                else -> break
            }
        }
        
        return expr
    }
    
    private fun primary(): Expr {
        val line = peek().line
        
        return when {
            match(TokenType.TRUE) -> LiteralExpr(true, line)
            match(TokenType.FALSE) -> LiteralExpr(false, line)
            match(TokenType.NULL) -> LiteralExpr(null, line)
            match(TokenType.NUMBER) -> LiteralExpr(previous().literal, line)
            match(TokenType.STRING) -> LiteralExpr(previous().literal, line)
            match(TokenType.IDENTIFIER) -> {
                val name = previous()
                // Check for object literal: ClassName { ... }
                if (check(TokenType.LBRACE) && name.lexeme[0].isUpperCase()) {
                    advance() // consume '{'
                    val fields = mutableMapOf<String, Expr>()
                    if (!check(TokenType.RBRACE)) {
                        do {
                        val fieldName = consumeIdentifier("Expected field name")
                            consume(TokenType.COLON, "Expected ':' after field name")
                            val value = expression()
                            fields[fieldName.lexeme] = value
                        } while (match(TokenType.COMMA))
                    }
                    consume(TokenType.RBRACE, "Expected '}' after object fields")
                    ObjectExpr(name.lexeme, fields, line)
                } else {
                    IdentifierExpr(name.lexeme, line)
                }
            }
            match(TokenType.LPAREN) -> {
                val expr = expression()
                consume(TokenType.RPAREN, "Expected ')' after expression")
                expr
            }
            match(TokenType.LBRACKET) -> {
                val items = mutableListOf<Expr>()
                if (!check(TokenType.RBRACKET)) {
                    do {
                        items.add(expression())
                    } while (match(TokenType.COMMA))
                }
                consume(TokenType.RBRACKET, "Expected ']' after list")
                ListExpr(items, line)
            }
            match(TokenType.LBRACE) -> {
                val entries = mutableListOf<Pair<String, Expr>>()
                if (!check(TokenType.RBRACE)) {
                    do {
                        val key = consume(TokenType.STRING, "Expected key")
                        consume(TokenType.COLON, "Expected ':' after key")
                        val value = expression()
                        entries.add((key.literal as String) to value)
                    } while (match(TokenType.COMMA))
                }
                consume(TokenType.RBRACE, "Expected '}' after map")
                MapExpr(entries, line)
            }
            match(TokenType.FUN) -> {
                consume(TokenType.LPAREN, "Expected '(' for lambda")
                val params = mutableListOf<String>()
                if (!check(TokenType.RPAREN)) {
                    do {
                        val param = consumeIdentifier("Expected parameter")
                        params.add(param.lexeme)
                    } while (match(TokenType.COMMA))
                }
                consume(TokenType.RPAREN, "Expected ')' after lambda parameters")
                consume(TokenType.LBRACE, "Expected '{' before lambda body")
                val body = block()
                LambdaExpr(params, body, line)
            }
            match(TokenType.IF) -> {
                val condition = expression()
                consumeIdentifier("Expected 'then'") // then
                val thenBranch = expression()
                consume(TokenType.ELSE, "Expected 'else'")
                val elseBranch = expression()
                IfExpr(condition, thenBranch, elseBranch, line)
            }
            else -> throw RuntimeException("Unexpected token: ${peek()}")
        }
    }
    
    // Helper methods
    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }
    
    private fun check(type: TokenType) = !isAtEnd() && peek().type == type
    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }
    private fun isAtEnd() = peek().type == TokenType.EOF
    private fun peek() = tokens[current]
    private fun previous() = tokens[current - 1]
    
    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw RuntimeException("$message (Found ${peek().type} '${peek().lexeme}' at line ${peek().line})")
    }
    
    private fun consumeIdentifier(message: String): Token {
        val next = peek()
        val type = next.type
        if (type == TokenType.IDENTIFIER || type == TokenType.TYPE || type == TokenType.NULL || 
            type == TokenType.STRING || type == TokenType.NUMBER || type == TokenType.BOOL || 
            type == TokenType.LIST || type == TokenType.MAP || type == TokenType.NATIVE || 
            type == TokenType.DEFAULT || type == TokenType.IN || type == TokenType.TEST || 
            type == TokenType.ASSERT || type == TokenType.TRY || type == TokenType.CATCH || 
            type == TokenType.DELETE || type == TokenType.VAR || type == TokenType.CONST || 
            type == TokenType.IF || type == TokenType.ELSE || type == TokenType.WHILE || 
            type == TokenType.FOR || type == TokenType.RETURN || type == TokenType.EXPORT || 
            type == TokenType.IMPORT || type == TokenType.FROM || type == TokenType.AS ||
            type == TokenType.FUN || type == TokenType.METHOD || type == TokenType.FIELD) {
            return advance()
        }
        throw RuntimeException("$message (Found ${next.type} '${next.lexeme}' at line ${next.line})")
    }
    
    private fun synchronize() {
        if (isAtEnd()) return
        advance()
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return
            when (peek().type) {
                TokenType.CLASS, TokenType.FUN, TokenType.VAR, TokenType.FOR,
                TokenType.IF, TokenType.WHILE, TokenType.RETURN,
                TokenType.METHOD, TokenType.FIELD -> return
                else -> advance()
            }
        }
    }
}
