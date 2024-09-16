package be.sweetmustard.springrestdocsgenerator

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent


class GenerateRestDocsTestAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        println("Plugin action performed")
    }
    
    
}