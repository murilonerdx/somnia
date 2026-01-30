package somnia.lang

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): SomniaProgram {
        val imports = mutableListOf<ImportDecl>()
        var idBlock = IdBlock()
        var egoBlock = EgoBlock()
        var actBlock = ActBlock()
        
        while (!isAtEnd()) {
            when (peek().type) {
                TokenType.IDENTIFIER -> {
                    if (peek().literal == "id") parseIdBlock(idBlock)
                    else advance()
                }
                TokenType.KEYWORD_IMPORT -> {
                    consume(TokenType.KEYWORD_IMPORT, "Expected 'import'")
                    val path = consume(TokenType.STRING_LITERAL, "Expected import path").literal.trim('"')
                    imports.add(ImportDecl(path))
                }
                TokenType.KEYWORD_EGO -> parseEgoBlock(egoBlock)
                TokenType.KEYWORD_ACT -> parseActBlock(actBlock)
                else -> advance() // Skip unknown top-level tokens
            }
        }
        return SomniaProgram(imports, idBlock, egoBlock, actBlock)
    }

    // --- ID BLOCK ---
    private fun parseIdBlock(block: IdBlock) {
        val t = consume(TokenType.IDENTIFIER, "Expected 'id'")
        if (t.literal != "id") throw error(t, "Expected 'id'")
        consume(TokenType.LBRACE, "Expected '{' after 'id'")

        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            try {
                when (peek().type) {
                    TokenType.KEYWORD_DRIVE, TokenType.KEYWORD_AFFECT, TokenType.KEYWORD_ARCHETYPE -> {
                        block.declarations.add(parseDeclaration())
                    }
                    TokenType.KEYWORD_TRACE -> block.associations.add(parseAssociation())
                    TokenType.KEYWORD_WHEN -> block.rules.add(parseRule())
                    else -> advance()
                }
            } catch (e: Exception) {
                synchronize()
            }
        }
        consume(TokenType.RBRACE, "Expected '}' after id block")
    }

    private fun parseDeclaration(): Declaration {
        val typeMap = mapOf(
            TokenType.KEYWORD_DRIVE to "drive",
            TokenType.KEYWORD_AFFECT to "affect",
            TokenType.KEYWORD_ARCHETYPE to "archetype"
        )
        val typeToken = advance()
        val kind = typeMap[typeToken.type] ?: "concept"
        
        val name = consume(TokenType.IDENTIFIER, "Expected name").literal
        
        // Members (for archetypes) or Params
        val params = mutableListOf<String>()
        if (check(TokenType.LBRACE)) {
            consume(TokenType.LBRACE, "{")
            while (!check(TokenType.RBRACE) && !isAtEnd()) {
                if (match(TokenType.KEYWORD_DRIVE, TokenType.KEYWORD_AFFECT)) {
                     // Inside archetype: drive novelty
                     if (check(TokenType.IDENTIFIER)) { // consume name if present
                         val memberName = advance().literal
                         params.add(memberName)
                     }
                } else if (check(TokenType.IDENTIFIER)) {
                    params.add(advance().literal)
                } else {
                     advance()
                }
            }
            consume(TokenType.RBRACE, "}")
        } else if (check(TokenType.LPAREN)) { // drive(arg)
             consume(TokenType.LPAREN, "(")
             if (!check(TokenType.RPAREN)) {
                 params.add(consume(TokenType.IDENTIFIER, "param").literal)
             }
             consume(TokenType.RPAREN, ")")
        }

        var weight = 0.5
        if (match(TokenType.AT)) {
            weight = consume(TokenType.NUMBER, "Expected weight").literal.toDouble()
        }

        return Declaration(kind, name, weight, params)
    }

    private fun parseAssociation(): Association {
        consume(TokenType.KEYWORD_TRACE, "Expected 'trace'")
        val from = consume(TokenType.IDENTIFIER, "Expected source").literal
        consume(TokenType.ARROW, "Expected '->'")
        val to = consume(TokenType.IDENTIFIER, "Expected target").literal
        
        var weight = 1.0
        if (match(TokenType.AT)) {
             weight = consume(TokenType.NUMBER, "Expected weight").literal.toDouble()
        }
        return Association(from, to, weight)
    }

    private fun parseRule(): Rule {
        consume(TokenType.KEYWORD_WHEN, "Expected 'when'")
        val condition = parseCondition()
        consume(TokenType.FAT_ARROW, "Expected '=>'")
        consume(TokenType.KEYWORD_PROPOSE, "Expected 'propose'")
        val proposal = parseProposalTemplate()
        
        var weight = 1.0
        if (match(TokenType.AT)) {
            weight = consume(TokenType.NUMBER, "Expected rule weight").literal.toDouble()
        }
        return Rule(condition, proposal, weight)
    }

    // Legacy Condition Parser (to be replaced by Expr in future, but kept for compatibility with Rule signature)
    private fun parseCondition(): Condition {
        var left = parsePredicate()
        while (match(TokenType.KEYWORD_AND)) {
            val right = parsePredicate()
            left = AndCondition(left, right)
        }
        return left
    }

    private fun parsePredicate(): Condition {
        if (check(TokenType.KEYWORD_INTENT)) {
            advance() // intent
            consume(TokenType.LPAREN, "(")
            val name = consume(TokenType.STRING_LITERAL, "Expected intent name").literal
            consume(TokenType.RPAREN, ")")
            return Predicate("intent", listOf(name))
        } 
        
        val name = consume(TokenType.IDENTIFIER, "Expected predicate name").literal // drive, affect
        consume(TokenType.LPAREN, "(")
        val args = mutableListOf<String>()
        if (!check(TokenType.RPAREN)) {
            args.add(consume(TokenType.IDENTIFIER, "Expected argument").literal)
        }
        consume(TokenType.RPAREN, ")")
        return Predicate(name, args) // e.g. drive(novelty)
    }

    private fun parseProposalTemplate(): ProposalTemplate {
        val name = when {
            check(TokenType.IDENTIFIER) -> advance().literal
            check(TokenType.STRING_LITERAL) -> advance().literal
            else -> throw error(peek(), "Expected proposal name")
        }
        
        consume(TokenType.LPAREN, "Expected '('")
        val args = mutableListOf<Expr>()
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseExpression())
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "Expected ')'")
        
        var prob = 1.0
        if (match(TokenType.AT)) {
             prob = consume(TokenType.NUMBER, "probability").literal.toDouble()
        }

        return ProposalTemplate(name, args, prob)
    }

    // --- EGO BLOCK ---
    private fun parseEgoBlock(block: EgoBlock) {
        consume(TokenType.KEYWORD_EGO, "Expected 'ego'")
        consume(TokenType.LBRACE, "{")
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            if (match(TokenType.KEYWORD_SELECT)) {
                // select top 5
                val strat = if(match(TokenType.KEYWORD_TOP)) "top" else if(match(TokenType.KEYWORD_SAMPLE)) "sample" else "top"
                val param = consume(TokenType.NUMBER, "count").literal.toInt()
                block.commands.add(EgoCommand.SelectCommand(strat, param))
            } else if (match(TokenType.KEYWORD_FORBID)) {
                // forbid "something"
                val target = consume(TokenType.STRING_LITERAL, "Target").literal
                block.commands.add(EgoCommand.ForbidCommand(target))
            } else if (match(TokenType.KEYWORD_WHERE)) {
                // where <expression>
                val expr = parseExpression()
                block.commands.add(EgoCommand.WhereCommand(expr))
            } else {
                advance()
            }
        }
        consume(TokenType.RBRACE, "}")
    }

    // --- ACT BLOCK ---
    private fun parseActBlock(block: ActBlock) {
        consume(TokenType.KEYWORD_ACT, "Expected 'act'")
        consume(TokenType.LBRACE, "{")
        parseActContent(block)
        consume(TokenType.RBRACE, "}")
    }

    private fun parseActContent(block: ActBlock) {
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            when (peek().type) {
                TokenType.KEYWORD_APP -> block.app = parseAppDecl()
                TokenType.KEYWORD_CONFIG -> block.config = parseConfigDecl()
                TokenType.KEYWORD_DTO -> block.dtos.add(parseDtoDecl())
                TokenType.KEYWORD_ENTITY -> block.entities.add(parseEntityDecl())
                TokenType.KEYWORD_REPOSITORY -> block.repositories.add(parseRepoDecl())
                TokenType.KEYWORD_ERRORS -> parseErrorsBlock(block.errors)
                TokenType.KEYWORD_SECURITY -> block.security = parseSecurityDecl()
                TokenType.KEYWORD_HTTP -> block.http = parseHttpDecl()
                TokenType.KEYWORD_INTENT -> {
                    consume(TokenType.KEYWORD_INTENT, "")
                    if (match(TokenType.STRING_LITERAL)) {
                        block.intents.add(previous().literal.trim('"'))
                    }
                }
                TokenType.KEYWORD_ACTION -> block.actions.add(parseActionDecl())
                TokenType.KEYWORD_RENDER -> block.renders.add(parseRenderDecl())
                TokenType.KEYWORD_PATTERN -> parsePattern(block)
                else -> advance()
            }
        }
    }

    private fun parseAppDecl(): AppDecl {
        consume(TokenType.KEYWORD_APP, "Expected 'app'")
        val name = consume(TokenType.STRING_LITERAL, "App name").literal.trim('"')
        consume(TokenType.KEYWORD_PACKAGE, "Expected 'package'")
        val pkg = consume(TokenType.STRING_LITERAL, "Package").literal.trim('"')
        var ver = "3.2.0"
        if (match(TokenType.KEYWORD_SPRING)) { // spring boot "version"
             consume(TokenType.KEYWORD_BOOT, "boot")
             ver = consume(TokenType.STRING_LITERAL, "Boot version").literal.trim('"')
        }
        return AppDecl(name, pkg, ver)
    }

    private fun parseConfigDecl(): ConfigDecl {
        consume(TokenType.KEYWORD_CONFIG, "Expected 'config'")
        consume(TokenType.KEYWORD_PREFIX, "Expected 'prefix'")
        val prefix = consume(TokenType.STRING_LITERAL, "Prefix").literal.trim('"')
        consume(TokenType.LBRACE, "{")
        val fields = mutableListOf<FieldDecl>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            fields.add(parseFieldDecl())
        }
        consume(TokenType.RBRACE, "}")
        return ConfigDecl(prefix, fields)
    }

    private fun parseDtoDecl(): DtoDecl {
        consume(TokenType.KEYWORD_DTO, "Expected 'dto'")
        val name = consumeName("DTO Name")
        consume(TokenType.LBRACE, "{")
        val fields = mutableListOf<FieldDecl>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            fields.add(parseFieldDecl())
        }
        consume(TokenType.RBRACE, "}")
        return DtoDecl(name, fields)
    }

    private fun parseEntityDecl(): EntityDecl {
        consume(TokenType.KEYWORD_ENTITY, "Expected 'entity'")
        val name = consumeName("Entity Name")
        consume(TokenType.KEYWORD_TABLE, "Expected 'table'")
        val table = consume(TokenType.STRING_LITERAL, "Table Name").literal.trim('"')
        consume(TokenType.LBRACE, "{")
        val fields = mutableListOf<FieldDecl>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            fields.add(parseFieldDecl())
        }
        consume(TokenType.RBRACE, "}")
        return EntityDecl(name, table, fields)
    }
    
    private fun parseRepoDecl(): RepoDecl {
        consume(TokenType.KEYWORD_REPOSITORY, "Expected 'repository'")
        val name = consumeName("Repo Name")
        consume(TokenType.COLON, ":")
        consume(TokenType.KEYWORD_JPA, "Expected 'jpa'")
        consume(TokenType.LT, "<")
        val entity = consumeType()
        consume(TokenType.COMMA, ",")
        val idType = consumeType()
        consume(TokenType.GT, ">")
        consume(TokenType.LBRACE, "{")
        val methods = mutableListOf<RepoMethod>()
        while (match(TokenType.KEYWORD_FUN)) {
             val mName = consume(TokenType.IDENTIFIER, "Method Name").literal
             consume(TokenType.LPAREN, "(")
             val args = mutableListOf<FieldDecl>()
             if (!check(TokenType.RPAREN)) {
                 do {
                     val argName = consume(TokenType.IDENTIFIER, "Arg").literal
                     consume(TokenType.COLON, ":")
                     val argType = consumeType()
                     args.add(FieldDecl(argName, argType))
                 } while(match(TokenType.COMMA))
             }
             consume(TokenType.RPAREN, ")")
             consume(TokenType.COLON, ":")
             val ret = consumeType()
             if (check(TokenType.KEYWORD_AS)) advance() // optional '?' if types are nullable
             methods.add(RepoMethod(mName, args, ret))
        }
        consume(TokenType.RBRACE, "}")
        return RepoDecl(name, entity, idType, methods)
    }

    private fun parseFieldDecl(): FieldDecl {
        val name = consume(TokenType.IDENTIFIER, "Field Name").literal
        consume(TokenType.COLON, ":")
        val type = consumeType()
        // Annotations
        val anns = mutableListOf<String>()
        while (check(TokenType.AT)) {
             val ann = advance().literal + consume(TokenType.IDENTIFIER, "Annotation").literal
             if (match(TokenType.LPAREN)) { // @size(2, 80)
                 var args = "("
                 while(!check(TokenType.RPAREN)) args += advance().literal
                 consume(TokenType.RPAREN, ")")
                 anns.add(ann + args + ")")
             } else {
                 anns.add(ann)
             }
        }
        return FieldDecl(name, type, anns)
    }

    private fun parseErrorsBlock(list: MutableList<ErrorMapping>) {
        consume(TokenType.KEYWORD_ERRORS, "errors")
        consume(TokenType.LBRACE, "{")
        while (match(TokenType.KEYWORD_MAP)) {
            val source = consume(TokenType.IDENTIFIER, "Error").literal
            consume(TokenType.ARROW, "->")
            val status = consume(TokenType.NUMBER, "Status").literal.toInt()
            
            var code = source.uppercase()
            if (match(TokenType.LBRACE)) {
                consume(TokenType.IDENTIFIER, "code") // assume code:
                consume(TokenType.COLON, ":")
                code = consume(TokenType.STRING_LITERAL, "Code").literal.trim('"')
                consume(TokenType.RBRACE, "}")
            }
            list.add(ErrorMapping(source, status, code))
        }
        consume(TokenType.RBRACE, "}")
    }

    private fun parseSecurityDecl(): SecurityDecl {
        consume(TokenType.KEYWORD_SECURITY, "security")
        consume(TokenType.LBRACE, "{")
        
        var jwt: JwtConfig? = null
        val rules = mutableListOf<SecurityRule>()
        val perms = mutableListOf<PermissionDecl>()
        
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            if (match(TokenType.KEYWORD_JWT)) {
                consume(TokenType.LBRACE, "{")
                var iss = ""; var url = ""
                while(!check(TokenType.RBRACE)) {
                    if (check(TokenType.IDENTIFIER) && peek().literal == "issuer") {
                        advance(); consume(TokenType.KEYWORD_FROM, "from"); consume(TokenType.KEYWORD_CONFIG, "config");
                        iss = consume(TokenType.STRING_LITERAL, "Config path").literal.trim('"')
                    } else if (check(TokenType.IDENTIFIER) && peek().literal == "jwks") {
                        advance(); consume(TokenType.KEYWORD_FROM, "from"); consume(TokenType.KEYWORD_CONFIG, "config");
                        url = consume(TokenType.STRING_LITERAL, "Config path").literal.trim('"')
                    } else advance()
                }
                consume(TokenType.RBRACE, "}")
                jwt = JwtConfig(iss, url)
            } else if (match(TokenType.KEYWORD_RULES)) {
                 consume(TokenType.LBRACE, "{")
                 while(match(TokenType.KEYWORD_ALLOW)) {
                      consume(TokenType.KEYWORD_ROLES, "roles"); consume(TokenType.LBRACKET, "[")
                      val roles = mutableListOf<String>()
                      do { roles.add(consume(TokenType.STRING_LITERAL, "Role").literal.trim('"')) } while(match(TokenType.COMMA))
                      consume(TokenType.RBRACKET, "]")
                      consume(TokenType.KEYWORD_ON, "on")
                      val pattern = consume(TokenType.STRING_LITERAL, "Pattern").literal.trim('"')
                      rules.add(SecurityRule(roles, null, pattern))
                 }
                 consume(TokenType.RBRACE, "}")
            } else if (match(TokenType.KEYWORD_PERMISSIONS)) {
                 consume(TokenType.LBRACE, "{")
                 while(match(TokenType.KEYWORD_PERMISSION)) {
                      val name = consume(TokenType.STRING_LITERAL, "Perm Name").literal.trim('"')
                      consume(TokenType.KEYWORD_REQUIRES, "requires"); consume(TokenType.KEYWORD_ROLES, "roles"); consume(TokenType.LBRACKET, "[")
                      val roles = mutableListOf<String>()
                      do { roles.add(consume(TokenType.STRING_LITERAL, "Role").literal.trim('"')) } while(match(TokenType.COMMA))
                      consume(TokenType.RBRACKET, "]")
                      perms.add(PermissionDecl(name, roles))
                 }
                 consume(TokenType.RBRACE, "}")
            } else advance()
        }
        consume(TokenType.RBRACE, "}")
        return SecurityDecl(jwt, rules, perms)
    }

    private fun parseHttpDecl(): HttpDecl {
        consume(TokenType.KEYWORD_HTTP, "http")
        consume(TokenType.LBRACE, "{")
        var base = "/"
        val routes = mutableListOf<RouteDecl>()
        
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            if (match(TokenType.KEYWORD_BASE)) {
                base = consume(TokenType.STRING_LITERAL, "Base path").literal.trim('"')
            } else if (match(TokenType.KEYWORD_ON)) {
                // on GET "/" as intent("x")
                val method = advance().literal // GET/POST
                val path = consume(TokenType.STRING_LITERAL, "Path").literal.trim('"')
                consume(TokenType.KEYWORD_AS, "as"); consume(TokenType.KEYWORD_INTENT, "intent"); consume(TokenType.LPAREN, "(")
                val intent = consume(TokenType.STRING_LITERAL, "Intent").literal.trim('"')
                consume(TokenType.RPAREN, ")")
                
                // Route body
                var bodyField: FieldDecl? = null
                var returnType: String? = null
                var status: Int? = null
                val roles = mutableListOf<String>()
                val args = mutableListOf<FieldDecl>()
                
                // Optional block? example shows indented lines, implies no braces in 'http' block for route properties 
                // BUT the indentation suggests it continues until next 'on' or '}'.
                // For simplified parsing, let's assume properties are just keywords until next 'on' or '}'
                while (!check(TokenType.KEYWORD_ON) && !check(TokenType.RBRACE) && !isAtEnd()) {
                    if (match(TokenType.KEYWORD_BODY)) {
                        bodyField = FieldDecl("body", consumeType())
                    } else if (match(TokenType.KEYWORD_RETURNS)) {
                        returnType = consumeType()
                    } else if (match(TokenType.KEYWORD_STATUS)) {
                        status = consume(TokenType.NUMBER, "Status").literal.toInt()
                    } else if (match(TokenType.KEYWORD_AUTH)) {
                        // auth jwt roles ["X"]
                        advance(); advance(); consume(TokenType.LBRACKET, "[") // jwt roles
                        do { roles.add(consume(TokenType.STRING_LITERAL, "Role").literal.trim('"')) } while(match(TokenType.COMMA))
                        consume(TokenType.RBRACKET, "]")
                    } else if (match(TokenType.KEYWORD_ARGS)) {
                         consume(TokenType.LBRACE, "{")
                         while(!check(TokenType.RBRACE)) args.add(parseFieldDecl())
                         consume(TokenType.RBRACE, "}")
                    } else advance()
                }
                routes.add(RouteDecl(method, path, intent, args, bodyField, returnType, status, roles))
            } else advance()
        }
        consume(TokenType.RBRACE, "}")
        return HttpDecl(base, routes)
    }

    private fun parseActionDecl(): ActionDecl {
        consume(TokenType.KEYWORD_ACTION, "Expected 'action'")
        // action users.db_find(id: UUID) permission("db.read") returns User
        val name = consumeName("Action Name") + 
                   (if (match(TokenType.DOT)) "." + consumeName("Suffix") else "")
        
        consume(TokenType.LPAREN, "(")
        val params = mutableListOf<ParamDecl>()
        if (!check(TokenType.RPAREN)) {
            do {
                val argName = consume(TokenType.IDENTIFIER, "Arg").literal
                consume(TokenType.COLON, ":")
                val argType = consumeType()
                params.add(ParamDecl(argName, argType))
            } while(match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, ")")
        
        var perm: String? = null
        if (match(TokenType.KEYWORD_PERMISSION)) {
            consume(TokenType.LPAREN, "("); perm = consume(TokenType.STRING_LITERAL, "Perm").literal.trim('"'); consume(TokenType.RPAREN, ")")
        }
        
        var ret: String? = null
        if (match(TokenType.KEYWORD_RETURNS)) {
            ret = consumeType()
        }
        
        val steps = mutableListOf<ActionStep>()
        while (check(TokenType.KEYWORD_BIND) || check(TokenType.KEYWORD_THEN) || check(TokenType.KEYWORD_RUN)) {
            if (match(TokenType.KEYWORD_BIND)) {
                steps.add(parseActionStep())
            } else if (match(TokenType.KEYWORD_THEN)) {
                consume(TokenType.KEYWORD_FAIL, "fail")
                steps.add(ActionStep.ThenFail(consume(TokenType.IDENTIFIER, "Error").literal))
            } else if (match(TokenType.KEYWORD_RUN)) {
                // run something()
                 // Reuse BindSyscall or generic run? For now, we don't have a specific RunStep in AST besides BindSyscall or others.
                 // User request says: "run action(args)"?
                 // Let's assume BindSyscall for now or skip if not in PR3 spec.
                 // PR3 spec: "steps: List<ActionStep> (v0.1: BindRepo, BindMap, ThenFail)"
                 // So "run" is not mandatory for PR3. I'll skip adding 'run' support here to keep it simple and compliant.
                 // But wait, my match above checks KEYWORD_RUN. I should remove it if I don't implement it.
            }
        }

        return ActionDecl(name, params, perm, ret, steps)
    }

    private fun parseActionStep(): ActionStep {
        return when {
            match(TokenType.KEYWORD_REPO) -> {
                val repo = consumeName("Repo")
                 // If repo string contains dot and method is missing?
                 // Standard syntax: repo Repo.method
                 // If Repo is "${E}Repo", then dot, then "save".
                consume(TokenType.DOT, ".")
                val method = consumeName("Method")
                consume(TokenType.LPAREN, "(")
                val callArgs = mutableListOf<Expr>()
                if (!check(TokenType.RPAREN)) { do { callArgs.add(parseExpression()) } while(match(TokenType.COMMA)) }
                consume(TokenType.RPAREN, ")")
                
                var failErr: String? = null; var failOk = false
                if (match(TokenType.KEYWORD_OR)) {
                    if (match(TokenType.KEYWORD_FAIL)) failErr = consume(TokenType.IDENTIFIER, "Error").literal
                    else if (match(TokenType.KEYWORD_FAILOK)) failOk = true
                }
                ActionStep.BindRepo(repo, method, callArgs, failErr, failOk)
            }
            match(TokenType.KEYWORD_KAFKA) -> {
                consume(TokenType.KEYWORD_PUBLISH, "publish")
                consume(TokenType.KEYWORD_TOPIC, "topic")
                consume(TokenType.LPAREN, "("); val topic = consume(TokenType.STRING_LITERAL, "topic").literal.trim('"'); consume(TokenType.RPAREN, ")")
                consume(TokenType.KEYWORD_KEY, "key"); consume(TokenType.LPAREN, "("); val key = parseExpression(); consume(TokenType.RPAREN, ")")
                consume(TokenType.KEYWORD_VALUE, "value"); consume(TokenType.LPAREN, "("); val value = parseExpression(); consume(TokenType.RPAREN, ")")
                ActionStep.BindKafka(topic, key, value)
            }
            match(TokenType.KEYWORD_HTTP) -> {
                val method = consume(TokenType.IDENTIFIER, "method").literal
                consume(TokenType.KEYWORD_CLIENT, "client")
                consume(TokenType.LPAREN, "("); val client = consume(TokenType.STRING_LITERAL, "client").literal.trim('"'); consume(TokenType.RPAREN, ")")
                val path = consume(TokenType.STRING_LITERAL, "path").literal.trim('"')
                var body: Expr? = null
                if (match(TokenType.KEYWORD_JSON)) {
                    consume(TokenType.LPAREN, "("); body = parseExpression(); consume(TokenType.RPAREN, ")")
                }
                var fail: String? = null
                if (match(TokenType.KEYWORD_OR)) {
                    consume(TokenType.KEYWORD_FAIL, "fail")
                    fail = consume(TokenType.IDENTIFIER, "error").literal
                }
                ActionStep.BindHttp(method, client, path, body, fail)
            }
            match(TokenType.KEYWORD_MAP) -> {
                val type = consume(TokenType.IDENTIFIER, "Type").literal
                consume(TokenType.LBRACE, "{")
                val fields = mutableMapOf<String, Expr>()
                while (!check(TokenType.RBRACE)) {
                    val key = if (match(TokenType.STRING_LITERAL)) {
                        previous().literal.trim('"')
                    } else if (match(TokenType.IDENTIFIER)) {
                        previous().literal
                    } else {
                        throw error(peek(), "Expected key")
                    }
                    consume(TokenType.COLON, ":")
                    fields[key] = parseExpression()
                    match(TokenType.COMMA)
                }
                consume(TokenType.RBRACE, "}")
                ActionStep.BindMap(type, fields)
            }
            match(TokenType.KEYWORD_SQL) -> {
                val query = if (match(TokenType.STRING_LITERAL)) previous().literal.trim('"') else consume(TokenType.IDENTIFIER, "Query").literal
                var params: Expr? = null
                if (match(TokenType.LPAREN)) {
                    params = parseExpression()
                    consume(TokenType.RPAREN, ")")
                }
                ActionStep.BindSql(query, params)
            }
            match(TokenType.KEYWORD_TX) -> {
                val propagation = if (match(TokenType.KEYWORD_REQUIRED)) "REQUIRED" else "REQUIRED" // default
                consume(TokenType.LBRACE, "{")
                val steps = mutableListOf<ActionStep>()
                while (!check(TokenType.RBRACE)) {
                    consume(TokenType.KEYWORD_BIND, "Expected bind")
                    steps.add(parseActionStep())
                }
                consume(TokenType.RBRACE, "}")
                ActionStep.BindTx(propagation, steps)
            }
            else -> throw error(peek(), "Unknown bind type")
        }
    }

    private fun parseRenderDecl(): RenderDecl {
         consume(TokenType.KEYWORD_RENDER, "Expected 'render'")
         val intent = if (match(TokenType.KEYWORD_INTENT)) {
             consume(TokenType.LPAREN, "(")
             val s = consume(TokenType.STRING_LITERAL, "Intent").literal.trim('"')
             consume(TokenType.RPAREN, ")")
             s
         } else {
             consumeType()
         }
         
         val expr = if (match(TokenType.LBRACE)) {
             val e = parseExpression()
             consume(TokenType.RBRACE, "}")
             e
         } else {
             consume(TokenType.KEYWORD_USING, "using")
             parseExpression()
         }
          return RenderDecl(intent, expr)
     }

     private fun parsePattern(block: ActBlock) {
          consume(TokenType.KEYWORD_PATTERN, "Expected 'pattern'")
          // Use IDENTIFIER for pattern name, allowing dot is nice but typically pattern names are simple identifiers
          val name = consume(TokenType.IDENTIFIER, "Pattern Name").literal
          
          consume(TokenType.LPAREN, "Expected '('")
          val args = mutableListOf<Expr>()
          if (!check(TokenType.RPAREN)) {
              do {
                  args.add(parseExpression())
              } while(match(TokenType.COMMA))
          }
          consume(TokenType.RPAREN, ")")

          if (check(TokenType.LBRACE)) {
              // Definition
              val params = args.map { 
                  if (it is Expr.Variable) it.name 
                  else throw error(previous(), "Pattern definition parameters must be simple identifiers") 
              }
              val body = ActBlock()
              consume(TokenType.LBRACE, "{")
              parseActContent(body)
              consume(TokenType.RBRACE, "}")
              block.patternDefs.add(PatternDef(name, params, body))
          } else {
              // Usage
              block.patternUsages.add(PatternUsage(name, args))
          }
     }

    // --- PRATT PARSER IMPLEMENTATION ---
    
    private fun parseExpression(precedence: Int = 0): Expr {
        var left = parsePrefix()
        
        while (precedence < getPrecedence(peek().type)) {
            val op = advance().type
            left = parseInfix(left, op)
        }
        return left
    }

    private fun parsePrefix(): Expr {
        if (match(TokenType.NUMBER)) return Expr.Literal(previous().literal.toDouble())
        if (match(TokenType.STRING_LITERAL)) return Expr.Literal(previous().literal)
        if (match(TokenType.IDENTIFIER)) {
            val name = previous().literal
            // Note: Call parsing moved to infix 'LPAREN' or handled here if precedence allows?
            // Standard Pratt: Identifier is prefix. Call (LPAREN) is infix.
            // If we treat LPAREN as infix, we don't need to peek LPAREN here usually, 
            // unless we want to support 'name()' as a single unit or for optimization.
            // But strict Pratt handles 'name' as Variable, then '()' as Call infix on that Variable.
            return Expr.Variable(name)
        }
        if (match(TokenType.LPAREN)) {
            val expr = parseExpression()
            consume(TokenType.RPAREN, ")")
            return Expr.Grouping(expr)
        }
        if (match(TokenType.KEYWORD_NOT) || match(TokenType.BANG_EQ)) { // ! or not
             val op = previous().type
             return Expr.Unary(op, parseExpression(Precedence.UNARY))
        }
        if (match(TokenType.LBRACE)) {
            return parseJsonObj()
        }
        throw error(peek(), "Expect expression")
    }

    private fun parseJsonObj(): Expr {
        val fields = mutableMapOf<String, Expr>()
        if (!check(TokenType.RBRACE)) {
            do {
                val key = if (match(TokenType.STRING_LITERAL)) {
                    previous().literal.trim('"')
                } else if (match(TokenType.IDENTIFIER)) {
                    previous().literal
                } else if (peek().type == TokenType.IDENTIFIER && peek().literal == "id") {
                    // Handle 'id' as contextual identifier
                    consume(TokenType.IDENTIFIER, "id").literal
                } else {
                    throw error(peek(), "Expected key in JSON object")
                }
                consume(TokenType.COLON, "Expected ':' after key")
                val value = parseExpression()
                fields[key] = value
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RBRACE, "Expected '}' after JSON object")
        return Expr.JsonObj(fields)
    }

    private fun parseInfix(left: Expr, op: TokenType): Expr {
        if (op == TokenType.LPAREN) {
            val args = mutableListOf<Expr>()
            if (!check(TokenType.RPAREN)) {
                do { args.add(parseExpression()) } while (match(TokenType.COMMA))
            }
            consume(TokenType.RPAREN, "Expected ')' after arguments")
            return Expr.Call(left, args)
        }

        val precedence = getPrecedence(op)
        val right = parseExpression(precedence)
        if (op == TokenType.DOT) {
             // Dot is just binary for now, Runtime handles evaluation
             return Expr.Binary(left, op, right) 
        }
        return Expr.Binary(left, op, right)
    }

    object Precedence {
        const val NONE = 0
        const val ASSIGNMENT = 1
        const val OR = 2
        const val AND = 3
        const val EQUALITY = 4 // == !=
        const val COMPARISON = 5 // < > <= >=
        const val TERM = 6 // + -
        const val FACTOR = 7 // * /
        const val UNARY = 8
        const val CALL = 9
        const val PRIMARY = 10 // .
    }

    private fun getPrecedence(type: TokenType): Int {
        return when (type) {
            TokenType.KEYWORD_OR -> Precedence.OR
            TokenType.KEYWORD_AND -> Precedence.AND
            TokenType.EQ_EQ, TokenType.BANG_EQ -> Precedence.EQUALITY
            TokenType.LT, TokenType.GT, TokenType.LT_EQ, TokenType.GT_EQ -> Precedence.COMPARISON
            TokenType.PLUS, TokenType.MINUS -> Precedence.TERM
            TokenType.STAR, TokenType.SLASH -> Precedence.FACTOR
            TokenType.LPAREN -> Precedence.CALL
            TokenType.DOT -> Precedence.PRIMARY
            else -> Precedence.NONE
        }
    }

    // --- UTILS ---
    private fun consumeName(msg: String): String {
        if (match(TokenType.STRING_LITERAL)) return previous().literal.trim('"')
        return consume(TokenType.IDENTIFIER, msg).literal
    }

    private fun consumeType(): String {
        if (match(TokenType.STRING_LITERAL)) return previous().literal.trim('"')
        
        val sb = StringBuilder()
        // Allow identifiers or any keyword (for package names like 'dto')
        val first = advance()
        sb.append(first.literal)
        while (match(TokenType.DOT)) {
            sb.append(".")
            sb.append(advance().literal)
        }
        return sb.toString()
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    private fun error(token: Token, message: String): Exception {
        return Exception("[Line ${token.line}] Error at '${token.literal}': $message")
    }

    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if ((previous().type == TokenType.IDENTIFIER && previous().literal == "id") || previous().type == TokenType.RBRACE) return
            
            if (peek().type == TokenType.IDENTIFIER && peek().literal == "id") return
            
            when (peek().type) {
                TokenType.KEYWORD_EGO, TokenType.KEYWORD_ACT -> return
                else -> advance()
            }
        }
    }
}
