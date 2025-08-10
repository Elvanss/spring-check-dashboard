package com.github.elvanss.springcheckdashboard.uast

    import com.intellij.psi.PsiField
    import com.intellij.psi.PsiModifierListOwner
    import com.intellij.psi.JavaPsiFacade
    import org.jetbrains.uast.*
    import org.jetbrains.uast.expressions.UInjectionHost

    class SpringUast {
        object SpringAnn {
            // Mapping rút gọn → HTTP method
            private val shortcut = mapOf(
                "org.springframework.web.bind.annotation.GetMapping" to "GET",
                "org.springframework.web.bind.annotation.PostMapping" to "POST",
                "org.springframework.web.bind.annotation.PutMapping" to "PUT",
                "org.springframework.web.bind.annotation.DeleteMapping" to "DELETE",
                "org.springframework.web.bind.annotation.PatchMapping" to "PATCH"
            )

            const val REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping"
            const val CONTROLLER = "org.springframework.stereotype.Controller"
            const val REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController"

            fun isController(uClass: UClass): Boolean {
                // trực tiếp
                if (uClass.hasAnnotation(REST_CONTROLLER) || uClass.hasAnnotation(CONTROLLER)) return true
                // meta-annotation
                return uClass.uAnnotations.any { isMetaAnnotated(it, setOf(REST_CONTROLLER, CONTROLLER)) }
            }

            fun isMetaAnnotated(uAnn: UAnnotation, targets: Set<String>): Boolean {
                val annClass = uAnn.resolve() ?: return false
                val psi = annClass as PsiModifierListOwner
                val anns = psi.annotations.mapNotNull { it.qualifiedName }
                if (anns.any { it in targets }) return true
                // đệ quy 1 cấp nữa (đủ dùng cho hầu hết meta-annotation Spring)
                return psi.annotations.any { meta ->
                    meta.qualifiedName?.let { q -> q != uAnn.qualifiedName && isMetaAnnotated((UastFacade.convertElement(meta, null, UAnnotation::class.java) ?: return@any false) as UAnnotation, targets) } ?: false
                }
            }

            fun httpMethods(uAnnotations: List<UAnnotation>): List<String> {
                // Ưu tiên rút gọn
                uAnnotations.firstOrNull { it.qualifiedName in shortcut.keys }?.let {
                    return listOf(shortcut[it.qualifiedName]!!)
                }
                // RequestMapping(method = [RequestMethod.GET, ...])
                val rm = uAnnotations.firstOrNull { it.qualifiedName == REQUEST_MAPPING } ?: return emptyList()
                val enums = readEnumValues(rm, "method")
                return if (enums.isNotEmpty()) enums.map { it.substringAfterLast('.') } else emptyList()
            }

            fun paths(uAnnotations: List<UAnnotation>): List<String> {
                // value/path có thể là single hoặc array, có thể là hằng
                val ann = uAnnotations.firstOrNull {
                    it.qualifiedName in shortcut.keys || it.qualifiedName == REQUEST_MAPPING
                } ?: return emptyList()
                val vals = readStringValues(ann, listOf("value", "path"))
                return if (vals.isEmpty()) listOf("") else vals
            }

            fun produces(uAnnotations: List<UAnnotation>) =
                readStringValues(uAnnotations.firstOrNull { it.qualifiedName == REQUEST_MAPPING }, listOf("produces"))

            fun consumes(uAnnotations: List<UAnnotation>) =
                readStringValues(uAnnotations.firstOrNull { it.qualifiedName == REQUEST_MAPPING }, listOf("consumes"))

            private fun readStringValues(uAnn: UAnnotation?, names: List<String>): List<String> {
                if (uAnn == null) return emptyList()
                names.forEach { n ->
                    uAnn.findAttributeValue(n)?.let { return flattenStringExpr(it) }
                }
                // nếu không có tên → dùng “value” mặc định
                uAnn.findAttributeValue(null)?.let { return flattenStringExpr(it) }
                return emptyList()
            }

            private fun flattenStringExpr(expr: UExpression): List<String> = when (expr) {
                is ULiteralExpression -> listOf(expr.value?.toString() ?: "")
                is UInjectionHost -> listOf(expr.evaluateString() ?: expr.asRenderString().trim('"'))
                is UCallExpression -> listOf(expr.evaluateString() ?: expr.asRenderString().trim('"'))
                is UReferenceExpression -> listOf(resolveConstString(expr) ?: expr.asRenderString().trim('"'))
                is UExpressionList -> expr.expressions.flatMap { flattenStringExpr(it) }
                else -> listOf(expr.asRenderString().trim('"'))
            }

            private fun readEnumValues(uAnn: UAnnotation, name: String): List<String> {
                val attr = uAnn.findDeclaredAttributeValue(name) ?: return emptyList()
                return when (attr) {
                    is UQualifiedReferenceExpression -> listOf(attr.asRenderString())
                    is UReferenceExpression -> listOf(attr.asRenderString())
                    is UExpressionList -> attr.expressions.map { it.asRenderString() }
                    else -> listOf(attr.asRenderString())
                }
            }

            private fun resolveConstString(ref: UReferenceExpression): String? {
                val resolved = ref.resolve() as? PsiField ?: return null
                val initializer = resolved.initializer ?: return null
                return (JavaPsiFacade.getInstance(resolved.project).constantEvaluationHelper
                    .computeConstantExpression(initializer) as? String)
            }
        }

        fun UClass.hasAnnotation(fqn: String): Boolean =
            this.uAnnotations.any { it.qualifiedName == fqn }
    }