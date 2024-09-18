package be.sweetmustard.springrestdocsgenerator

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.util.*

class RestDocsHelper {
    companion object {
        fun getCorrespondingDocumentationTestFile(
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

        fun getDocumentationTestFileName(productionClass: PsiClass): String {
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

        fun getDocumentationTestForMethod(currentMethod: PsiMethod): PsiMethod? {
            val productionClass = currentMethod.containingClass!!
            println(productionClass.name)
            val testSourceRoots = getPossibleTestSourceRoots(productionClass)
            testSourceRoots.forEach(::println)
            for (testSourceRoot in testSourceRoots) {
                val correspondingDocumentationTestFile = getCorrespondingDocumentationTestFile(testSourceRoot, productionClass)
                println(correspondingDocumentationTestFile?.name)
                if (correspondingDocumentationTestFile != null) {
                    val documentationTestClass = PsiTreeUtil.getChildOfType(
                        correspondingDocumentationTestFile,
                        PsiClass::class.java
                    )
                    val documentationTestMethods = PsiTreeUtil.getChildrenOfType(
                        documentationTestClass,
                        PsiMethod::class.java
                    )
                    documentationTestMethods?.forEach { println(it.name) }
                    return documentationTestMethods?.firstOrNull { it.name == currentMethod.name + "Example" }
                }
            }
            return null
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

        fun getPossibleTestSourceRoots(productionMethod: PsiMethod): List<VirtualFile> {
            val containingClass = productionMethod.containingClass ?: return Collections.emptyList()
            return getPossibleTestSourceRoots(containingClass)
        }
    }
}