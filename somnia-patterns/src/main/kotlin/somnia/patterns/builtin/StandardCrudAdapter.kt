package somnia.patterns.builtin

import somnia.lang.*
import somnia.patterns.PatternAdapter

class StandardCrudAdapter : PatternAdapter {
    override fun expand(program: SomniaProgram, args: List<Expr>) {
        val entityNameExpr = args.firstOrNull() as? Expr.Variable
            ?: throw IllegalStateException("StandardCrud requires an Entity name as first argument")
        
        val entityName = entityNameExpr.name
        val entity = program.act.entities.find { it.name == entityName }
            ?: throw IllegalStateException("Entity $entityName not found for StandardCrud")

        val repoName = "${entityName}Repository"
        val folder = entity.table.lowercase()

        // 1. Generate Repository (only if not manually defined AND not already expanded)
        if (program.act.repositories.none { it.name == repoName }) {
            program.act.repositories.add(
                RepoDecl(
                    name = repoName,
                    entityType = entityName,
                    idType = "UUID",
                    methods = listOf(
                        RepoMethod("save", listOf(FieldDecl("item", entityName)), entityName),
                        RepoMethod("findAll", emptyList(), "List"),
                        RepoMethod("deleteById", listOf(FieldDecl("id", "UUID")), "Void")
                    )
                )
            )
        }

        // 2. Generate Actions
        // Create
        if (program.act.actions.none { it.name == "$folder.create" }) {
            program.act.actions.add(
                ActionDecl(
                    name = "$folder.create",
                    params = listOf(ParamDecl("req", "com.example.crud.dto.CreateProductRequest")),
                    permission = "db.write",
                    returnType = entityName,
                    steps = listOf(
                        ActionStep.BindTx("REQUIRED", listOf(
                            ActionStep.BindRepo(repoName, "save", listOf(Expr.Variable("req")))
                        ))
                    )
                )
            )
        }

        // List
        if (program.act.actions.none { it.name == "$folder.list" }) {
            program.act.actions.add(
                ActionDecl(
                    name = "$folder.list",
                    params = emptyList(),
                    permission = "db.read",
                    returnType = "Json",
                    steps = listOf(
                        ActionStep.BindSql("select * from ${entity.table}", null)
                    )
                )
            )
        }

        // 3. Generate HTTP Routes
        if (program.act.http == null) {
            program.act.http = HttpDecl("/api", mutableListOf())
        }
        val routes = program.act.http!!.routes as MutableList<RouteDecl>

        if (routes.none { it.path == "/$folder" && it.method == "POST" }) {
            routes.add(RouteDecl("POST", "/$folder", "$folder.create", body = FieldDecl("req", "com.example.crud.dto.CreateProductRequest")))
        }
        if (routes.none { it.path == "/$folder" && it.method == "GET" }) {
            routes.add(RouteDecl("GET", "/$folder", "$folder.list"))
        }

        // 4. Generate Renders
        if (program.act.renders.none { it.intent == "$folder.create" }) {
            program.act.renders.add(RenderDecl("$folder.create", Expr.Variable("$folder.create")))
        }
        if (program.act.renders.none { it.intent == "$folder.list" }) {
            program.act.renders.add(RenderDecl("$folder.list", Expr.Variable("$folder.list")))
        }
    }
}
