package be.sweetmustard.springrestdocsgenerator

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.util.*

class RestDocsHelper {
    companion object {
        fun getCorrespondingDocumentationTest(
            testSourceRoot: VirtualFile,
            productionClass: PsiClass
        ): PsiFile? {
            val documentationTestFileName = getDocumentationTestFileName(productionClass)
            val packageName: String = getPackageName(productionClass)
            val documentationTestVirtualFile =
                testSourceRoot.findFileByRelativePath(
                    packageName.replace(
                        ".",
                        "/"
                    ) + "/" + documentationTestFileName
                )
            var documentationTestFile: PsiFile? = null
            if (documentationTestVirtualFile != null) {
                documentationTestFile =
                    PsiManager.getInstance(productionClass.project).findFile(documentationTestVirtualFile)
            }
            return documentationTestFile
        }

        private fun getDocumentationTestFileName(productionClass: PsiClass): String {
            return productionClass.name + "DocumentationTest.java"
        }
        
        fun getPackageName(selectedClass: PsiClass): String {
            val selectedClassFile = selectedClass.containingFile
            var packageName = ""
            if (selectedClassFile is PsiJavaFile) {
                packageName = selectedClassFile.packageName
            }
            return packageName
        }

        fun getPossibleTestSourceRoots(productionClass: PsiClass): List<VirtualFile> {
            val module =
                ProjectRootManager.getInstance(productionClass.project).fileIndex
                    .getModuleForFile(productionClass.containingFile.virtualFile)
            if (module == null) {
                return Collections.emptyList()
            }
            return ModuleRootManager.getInstance(module)
                .getSourceRoots(JavaSourceRootType.TEST_SOURCE)
        }
    }
}