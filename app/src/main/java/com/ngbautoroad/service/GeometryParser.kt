package com.ngbautoroad.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class NodeData(val text: String, val bounds: Rect)

object GeometryParser {

    /**
     * Extrai todos os nós da árvore de acessibilidade contendo texto e suas respectivas posições na tela.
     */
    fun extractNodes(root: AccessibilityNodeInfo?): List<NodeData> {
        val list = mutableListOf<NodeData>()
        if (root == null) return list
        traverse(root, list, 0)
        return list
    }

    private fun traverse(node: AccessibilityNodeInfo, list: MutableList<NodeData>, depth: Int) {
        if (depth > 50) return // Prevenção contra StackOverflow em árvores muito profundas
        
        val rect = Rect()
        node.getBoundsInScreen(rect)
        
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { t ->
            list.add(NodeData(t, rect))
        }
        
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { cd ->
            if (list.none { it.text == cd && it.bounds == rect }) {
                list.add(NodeData(cd, rect))
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverse(child, list, depth + 1)
            try { child.recycle() } catch (_: Exception) {}
        }
    }

    /**
     * Busca um valor monetário baseado em proximidade geométrica.
     * Ex: Acha "R$" e procura o número (ex: "15,00") que esteja logo ao lado ou abaixo.
     */
    fun findValueNearAnchor(nodes: List<NodeData>, anchorText: String = "R$"): Double {
        val anchorNodes = nodes.filter { it.text.contains(anchorText, ignoreCase = true) }
        if (anchorNodes.isEmpty()) return 0.0

        for (anchor in anchorNodes) {
            // Busca por nós que contenham padrão de número: "15,00" ou "1.234,00"
            val numberRegex = Regex("""^\d{1,4}[.,]\d{2}$""")
            
            // Filtra candidatos próximos (até 150px para baixo e 300px para direita)
            val candidateNodes = nodes.filter {
                numberRegex.matches(it.text) &&
                (it.bounds.top >= anchor.bounds.top - 50 && it.bounds.bottom <= anchor.bounds.bottom + 150) &&
                (it.bounds.left >= anchor.bounds.left - 50 && it.bounds.right <= anchor.bounds.right + 300)
            }
            
            // Pega o mais próximo
            val bestNode = candidateNodes.minByOrNull { 
                Math.abs(it.bounds.top - anchor.bounds.top) + Math.abs(it.bounds.left - anchor.bounds.left) 
            }
            
            if (bestNode != null) {
                return bestNode.text.replace(",", ".").toDoubleOrNull() ?: 0.0
            }
        }
        return 0.0
    }

    /**
     * Busca a distância (km) baseado em proximidade geométrica.
     * Procura o número imediatamente à esquerda/acima do texto "km"
     */
    fun findDistanceNearAnchor(nodes: List<NodeData>, anchorText: String = "km"): Double {
        val anchorNodes = nodes.filter { it.text.equals(anchorText, ignoreCase = true) || it.text.endsWith(anchorText, ignoreCase = true) }
        if (anchorNodes.isEmpty()) return 0.0

        for (anchor in anchorNodes) {
            // Se o próprio texto tem o número embutido (ex: "2.5 km"), resolve normal
            val inlineRegex = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE)
            val inlineMatch = inlineRegex.find(anchor.text)
            if (inlineMatch != null) {
                return inlineMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            // Senão, procura um nó contendo número nas proximidades (esquerda ou acima)
            val numberRegex = Regex("""^\d+[.,]\d+$""")
            val candidateNodes = nodes.filter {
                numberRegex.matches(it.text) &&
                (it.bounds.bottom >= anchor.bounds.top - 100 && it.bounds.top <= anchor.bounds.bottom + 50) &&
                (it.bounds.right <= anchor.bounds.right + 50 && it.bounds.left >= anchor.bounds.left - 200)
            }
            
            val bestNode = candidateNodes.minByOrNull { 
                Math.abs(it.bounds.top - anchor.bounds.top) + Math.abs(it.bounds.right - anchor.bounds.left) 
            }
            
            if (bestNode != null) {
                return bestNode.text.replace(",", ".").toDoubleOrNull() ?: 0.0
            }
        }
        return 0.0
    }
}
