package somnia.bootstrap

/**
 * Interpreter for Somnia - executes AST nodes
 */
class SomniaInterpreter {
    private val globalEnv = Environment()
    private var currentEnv = globalEnv
    private val classes = mutableMapOf<String, ClassStmt>()
    private val tests = mutableListOf<TestStmt>()
    private val loadedFiles = mutableSetOf<String>()
    private var basePath: String = "."
    
    class ReturnValue(val value: SomniaValue) : Exception()
    
    init {
        Natives.register(globalEnv)
    }
    
    fun interpret(statements: List<Stmt>, path: String = ".") {
        this.basePath = path
        
        // Pass 1: Register declarations (classes, functions, types, extends, natives)
        for (stmt in statements) {
            println("[PASS 1] ${stmt::class.simpleName} at line ${stmt.line} in $path")
            registerDeclaration(stmt)
        }
        
        // Pass 2: Execute statements
        for (stmt in statements) {
            if (isExecutable(stmt)) {
                println("[PASS 2] Executing ${stmt::class.simpleName} at line ${stmt.line} in $path")
                execute(stmt)
            }
        }
    }
    
    private fun registerDeclaration(stmt: Stmt) {
        when (stmt) {
            is FunStmt -> {
                val fn = SomniaValue.SFunction(stmt.name, stmt.params, stmt.body, globalEnv)
                globalEnv.define(stmt.name, fn)
            }
            is ClassStmt -> {
                classes[stmt.name] = stmt
                val methods = mutableMapOf<String, SomniaValue.SFunction>()
                for (method in stmt.methods) {
                    methods[method.name] = SomniaValue.SFunction(
                        method.name, method.params, method.body, globalEnv
                    )
                }
                val classValue = SomniaValue.SClass(stmt.name, emptyMap(), methods)
                globalEnv.define(stmt.name, classValue)
            }
            is ExtendStmt -> {
                val classValue = globalEnv.get(stmt.className) as? SomniaValue.SClass
                if (classValue != null) {
                    val methods = classValue.methods.toMutableMap()
                    for (method in stmt.methods) {
                        methods[method.name] = SomniaValue.SFunction(
                            method.name, method.params, method.body, globalEnv
                        )
                    }
                    globalEnv.define(stmt.className, SomniaValue.SClass(stmt.className, classValue.fields, methods))
                }
            }
            is NativeFunStmt -> {
                if (globalEnv.get(stmt.name) == null) {
                    println("[WARN] Native function ${stmt.name} declared but not implemented by VM")
                }
            }
            is ImportStmt -> {
                handleImport(stmt)
            }
            else -> {}
        }
    }
    
    private fun isExecutable(stmt: Stmt): Boolean {
        return when (stmt) {
            is FunStmt, is ClassStmt, is TypeStmt, is NativeFunStmt, is ExtendStmt, is ImportStmt -> false
            else -> true
        }
    }
    
    private fun handleImport(stmt: ImportStmt) {
        val fullPath = if (stmt.path.startsWith("/") || stmt.path.contains(":")) {
            stmt.path
        } else {
            java.io.File(java.io.File(basePath).parent, stmt.path).absolutePath
        }
        
        if (loadedFiles.contains(fullPath)) return
        loadedFiles.add(fullPath)
        
        println("[IMPORT] $fullPath")
        val file = java.io.File(fullPath)
        if (file.exists()) {
            val source = file.readText()
            val lexer = SomniaLexer(source)
            val tokens = lexer.scanTokens()
            val parser = SomniaParser(tokens)
            val statements = parser.parse()
            
            println("[DEBUG] File $fullPath has ${statements.size} top-level statements:")
            for (s in statements) {
                println("  - ${s::class.simpleName} at line ${s.line}")
            }
            
            val savedBasePath = basePath
            basePath = fullPath
            
            // Register declarations from imported file
            for (s in statements) {
                registerDeclaration(s)
            }
            
            // Execute statements from imported file
            try {
                for (s in statements) {
                    if (isExecutable(s)) execute(s)
                }
            } catch (rv: ReturnValue) {
                // Top-level return in module is fine
            } finally {
                basePath = savedBasePath
            }
        } else {
            println("[WARN] Could not find import: ${stmt.path}")
        }
    }
    
    fun runTests(): Pair<Int, Int> {
        var passed = 0
        var failed = 0
        
        for (test in tests) {
            try {
                val testEnv = Environment(globalEnv)
                val savedEnv = currentEnv
                currentEnv = testEnv
                
                try {
                    for (stmt in test.body) {
                        execute(stmt)
                    }
                } finally {
                    currentEnv = savedEnv
                }
                
                println("✓ ${test.name}")
                passed++
            } catch (e: Exception) {
                println("✗ ${test.name}: ${e.message}")
                failed++
            }
        }
        
        return passed to failed
    }
    
    private fun execute(stmt: Stmt) {
        when (stmt) {
            is ExprStmt -> evaluate(stmt.expr)
            is VarStmt -> {
                val value = stmt.initializer?.let { evaluate(it) } ?: SomniaValue.NULL
                currentEnv.define(stmt.name, value)
            }
            is ConstStmt -> {
                val value = evaluate(stmt.value)
                currentEnv.define(stmt.name, value)
            }
            is AssignStmt -> {
                val value = evaluate(stmt.value)
                if (!currentEnv.set(stmt.name, value)) {
                    currentEnv.define(stmt.name, value)
                }
            }
            is BlockStmt -> {
                val blockEnv = Environment(currentEnv)
                val savedEnv = currentEnv
                currentEnv = blockEnv
                try {
                    for (s in stmt.statements) {
                        execute(s)
                    }
                } finally {
                    currentEnv = savedEnv
                }
            }
            is IfStmt -> {
                if (evaluate(stmt.condition).isTruthy()) {
                    execute(stmt.thenBranch)
                } else {
                    stmt.elseBranch?.let { execute(it) }
                }
            }
            is WhenStmt -> {
                if (evaluate(stmt.condition).isTruthy()) {
                    execute(stmt.thenBranch)
                }
            }
            is WhileStmt -> {
                while (evaluate(stmt.condition).isTruthy()) {
                    execute(stmt.body)
                }
            }
            is ForStmt -> {
                val iterable = evaluate(stmt.iterable)
                val items = when (iterable) {
                    is SomniaValue.SList -> iterable.items
                    is SomniaValue.SMap -> iterable.entries.keys.map { SomniaValue.str(it) }
                    is SomniaValue.SString -> iterable.value.map { SomniaValue.str(it.toString()) }
                    else -> listOf()
                }
                
                for (item in items) {
                    val loopEnv = Environment(currentEnv)
                    loopEnv.define(stmt.name, item)
                    val savedEnv = currentEnv
                    currentEnv = loopEnv
                    try {
                        execute(stmt.body)
                    } finally {
                        currentEnv = savedEnv
                    }
                }
            }
            is ReturnStmt -> {
                val value = stmt.value?.let { evaluate(it) } ?: SomniaValue.NULL
                throw ReturnValue(value)
            }
            is FunStmt -> {}
            is ClassStmt -> {}
            is ImportStmt -> {}
            is ExportStmt -> {}
            is TestStmt -> {
                tests.add(stmt)
            }
            is TypeStmt -> {}
            is ExtendStmt -> {}
            is TryStmt -> {
                try {
                    for (s in stmt.body) execute(s)
                } catch (e: Exception) {
                    if (e is ReturnValue) throw e
                    val catchEnv = Environment(currentEnv)
                    if (stmt.catchVar != null) {
                        catchEnv.define(stmt.catchVar, SomniaValue.str(e.message ?: "Unknown error"))
                    }
                    val savedEnv = currentEnv
                    currentEnv = catchEnv
                    try {
                        for (s in stmt.catchBody) execute(s)
                    } finally {
                        currentEnv = savedEnv
                    }
                }
            }
            is AssertStmt -> {
                if (!evaluate(stmt.expr).isTruthy()) {
                    throw RuntimeException("Assertion failed at line ${stmt.line}")
                }
            }
            is NativeFunStmt -> {}
        }
    }
    
    private fun evaluate(expr: Expr): SomniaValue {
        return when (expr) {
            is LiteralExpr -> when (val v = expr.value) {
                null -> SomniaValue.NULL
                is Boolean -> SomniaValue.bool(v)
                is Number -> SomniaValue.num(v)
                is String -> SomniaValue.str(v)
                else -> SomniaValue.NULL
            }
            is IdentifierExpr -> {
                currentEnv.get(expr.name) 
                    ?: throw RuntimeException("Undefined variable: ${expr.name} (Env: ${currentEnv})")
            }
            is BinaryExpr -> {
                val left = evaluate(expr.left)
                val right = evaluate(expr.right)
                
                when (expr.op) {
                    "+" -> when {
                        left is SomniaValue.SNumber && right is SomniaValue.SNumber -> 
                            SomniaValue.num(left.value + right.value)
                        left is SomniaValue.SString || right is SomniaValue.SString ->
                            SomniaValue.str(left.toSomniaString() + right.toSomniaString())
                        left is SomniaValue.SList -> {
                            val items = left.items.toMutableList()
                            when (right) {
                                is SomniaValue.SList -> items.addAll(right.items)
                                else -> items.add(right)
                            }
                            SomniaValue.SList(items)
                        }
                        else -> SomniaValue.NULL
                    }
                    "-" -> {
                        val l = (left as? SomniaValue.SNumber)?.value ?: 0.0
                        val r = (right as? SomniaValue.SNumber)?.value ?: 0.0
                        SomniaValue.num(l - r)
                    }
                    "*" -> {
                        val l = (left as? SomniaValue.SNumber)?.value ?: 0.0
                        val r = (right as? SomniaValue.SNumber)?.value ?: 0.0
                        SomniaValue.num(l * r)
                    }
                    "/" -> {
                        val l = (left as? SomniaValue.SNumber)?.value ?: 0.0
                        val r = (right as? SomniaValue.SNumber)?.value ?: 1.0
                        SomniaValue.num(l / r)
                    }
                    "%" -> {
                        val l = (left as? SomniaValue.SNumber)?.value ?: 0.0
                        val r = (right as? SomniaValue.SNumber)?.value ?: 1.0
                        SomniaValue.num(l % r)
                    }
                    "==" -> SomniaValue.bool(left == right)
                    "!=" -> SomniaValue.bool(left != right)
                    "<" -> {
                        val l = (left as? SomniaValue.SNumber)?.value ?: 0.0
                        val r = (right as? SomniaValue.SNumber)?.value ?: 0.0
                        SomniaValue.bool(l < r)
                    }
                    ">" -> {
                        val l = (left as? SomniaValue.SNumber)?.value ?: 0.0
                        val r = (right as? SomniaValue.SNumber)?.value ?: 0.0
                        SomniaValue.bool(l > r)
                    }
                    "<=" -> {
                        val l = (left as? SomniaValue.SNumber)?.value ?: 0.0
                        val r = (right as? SomniaValue.SNumber)?.value ?: 0.0
                        SomniaValue.bool(l <= r)
                    }
                    ">=" -> {
                        val l = (left as? SomniaValue.SNumber)?.value ?: 0.0
                        val r = (right as? SomniaValue.SNumber)?.value ?: 0.0
                        SomniaValue.bool(l >= r)
                    }
                    "and" -> SomniaValue.bool(left.isTruthy() && right.isTruthy())
                    "or" -> SomniaValue.bool(left.isTruthy() || right.isTruthy())
                    else -> SomniaValue.NULL
                }
            }
            is UnaryExpr -> {
                val operand = evaluate(expr.operand)
                when (expr.op) {
                    "-" -> {
                        val n = (operand as? SomniaValue.SNumber)?.value ?: 0.0
                        SomniaValue.num(-n)
                    }
                    "not" -> SomniaValue.bool(!operand.isTruthy())
                    else -> SomniaValue.NULL
                }
            }
            is CallExpr -> {
                val callee = evaluate(expr.callee)
                val args = expr.args.map { evaluate(it) }
                
                when (callee) {
                    is SomniaValue.SFunction -> {
                        val fnEnv = Environment(callee.closure)
                        callee.params.forEachIndexed { i, param ->
                            fnEnv.define(param, args.getOrElse(i) { SomniaValue.NULL })
                        }
                        
                        val savedEnv = currentEnv
                        currentEnv = fnEnv
                        
                        try {
                            val body = callee.body as List<Stmt>
                            for (stmt in body) {
                                execute(stmt)
                            }
                            SomniaValue.NULL
                        } catch (rv: ReturnValue) {
                            rv.value
                        } finally {
                            currentEnv = savedEnv
                        }
                    }
                    is SomniaValue.SNative -> {
                        callee.handler(args)
                    }
                    is SomniaValue.SClass -> {
                        val instance = SomniaValue.SInstance(callee.name, mutableMapOf())
                        val classStmt = classes[callee.name]
                        classStmt?.fields?.forEach { (name, defaultValue) ->
                            instance.fields[name] = defaultValue?.let { evaluate(it) } ?: SomniaValue.NULL
                        }
                        instance
                    }
                    else -> throw RuntimeException("Cannot call ${callee.type}")
                }
            }
            is GetExpr -> {
                val obj = evaluate(expr.obj)
                
                when (obj) {
                    is SomniaValue.SInstance -> {
                        obj.fields[expr.name]?.let { return it }
                        
                        val classValue = globalEnv.get(obj.className) as? SomniaValue.SClass
                        val method = classValue?.methods?.get(expr.name)
                        if (method != null) {
                            val methodEnv = Environment(method.closure)
                            methodEnv.define("self", obj)
                            return SomniaValue.SFunction(method.name, method.params, method.body, methodEnv)
                        }
                        
                        throw RuntimeException("Undefined property: ${expr.name}")
                    }
                    is SomniaValue.SMap -> {
                        obj.entries[expr.name] ?: SomniaValue.NULL
                    }
                    is SomniaValue.SString -> {
                        when (expr.name) {
                            "length" -> SomniaValue.num(obj.value.length)
                            "starts_with" -> SomniaValue.SNative("starts_with") { args ->
                                val prefix = (args.firstOrNull() as? SomniaValue.SString)?.value ?: ""
                                SomniaValue.bool(obj.value.startsWith(prefix))
                            }
                            "ends_with" -> SomniaValue.SNative("ends_with") { args ->
                                val suffix = (args.firstOrNull() as? SomniaValue.SString)?.value ?: ""
                                SomniaValue.bool(obj.value.endsWith(suffix))
                            }
                            "substring" -> SomniaValue.SNative("substring") { args ->
                                val start = (args.firstOrNull() as? SomniaValue.SNumber)?.value?.toInt() ?: 0
                                SomniaValue.str(obj.value.substring(start.coerceIn(0, obj.value.length)))
                            }
                            "split" -> SomniaValue.SNative("split") { args ->
                                val delimiter = (args.firstOrNull() as? SomniaValue.SString)?.value ?: ""
                                SomniaValue.SList(obj.value.split(delimiter).map { SomniaValue.str(it) }.toMutableList())
                            }
                            else -> throw RuntimeException("Unknown string method: ${expr.name}")
                        }
                    }
                    is SomniaValue.SList -> {
                        when (expr.name) {
                            "length" -> SomniaValue.num(obj.items.size)
                            else -> throw RuntimeException("Unknown list property: ${expr.name}")
                        }
                    }
                    else -> throw RuntimeException("Cannot get property on ${obj.type}")
                }
            }
            is SetExpr -> {
                val obj = evaluate(expr.obj)
                val value = evaluate(expr.value)
                
                when (obj) {
                    is SomniaValue.SInstance -> {
                        obj.fields[expr.name] = value
                        value
                    }
                    is SomniaValue.SMap -> {
                        obj.entries[expr.name] = value
                        value
                    }
                    else -> throw RuntimeException("Cannot set property on ${obj.type}")
                }
            }
            is IndexExpr -> {
                val obj = evaluate(expr.obj)
                val index = evaluate(expr.index)
                
                when (obj) {
                    is SomniaValue.SList -> {
                        val i = (index as? SomniaValue.SNumber)?.value?.toInt() ?: 0
                        obj.items.getOrElse(i) { SomniaValue.NULL }
                    }
                    is SomniaValue.SMap -> {
                        val key = (index as? SomniaValue.SString)?.value ?: index.toSomniaString()
                        obj.entries[key] ?: SomniaValue.NULL
                    }
                    is SomniaValue.SString -> {
                        val i = (index as? SomniaValue.SNumber)?.value?.toInt() ?: 0
                        if (i in obj.value.indices) SomniaValue.str(obj.value[i].toString())
                        else SomniaValue.NULL
                    }
                    else -> SomniaValue.NULL
                }
            }
            is ListExpr -> {
                SomniaValue.SList(expr.items.map { evaluate(it) }.toMutableList())
            }
            is MapExpr -> {
                val entries = mutableMapOf<String, SomniaValue>()
                for ((key, value) in expr.entries) {
                    entries[key] = evaluate(value)
                }
                SomniaValue.SMap(entries)
            }
            is LambdaExpr -> {
                SomniaValue.SFunction("lambda", expr.params, expr.body, currentEnv)
            }
            is IfExpr -> {
                if (evaluate(expr.condition).isTruthy()) {
                    evaluate(expr.thenBranch)
                } else {
                    expr.elseBranch?.let { evaluate(it) } ?: SomniaValue.NULL
                }
            }
            is ObjectExpr -> {
                val instance = SomniaValue.SInstance(expr.className, mutableMapOf())
                for ((name, value) in expr.fields) {
                    instance.fields[name] = evaluate(value)
                }
                instance
            }
        }
    }
}
