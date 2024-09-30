package be.sweetmustard.springrestdocsgenerator.generator

import be.sweetmustard.springrestdocsgenerator.RestDocsHelper
import be.sweetmustard.springrestdocsgenerator.settings.SpringRestDocsGeneratorState
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiUtil
import java.util.logging.Logger

class TestFileGenerator {
    private val logger = Logger.getLogger(TestFileGenerator::class.java.name)

    internal fun createOrGetDocumentationTestFile(
        restController: PsiClass,
        currentProject: Project,
        testSourceRoot: VirtualFile,
        elementFactory: PsiElementFactory,
        projectState: SpringRestDocsGeneratorState
    ): PsiFile {
        logger.info("Getting documentation test file for %s".format(restController.name))
        var documentationTestFile = RestDocsHelper.getCorrespondingDocumentationTestFile(
            testSourceRoot,
            restController
        )
        if (documentationTestFile == null) {
            logger.info("Creating documentation test file for %s".format(restController.name))
            val fileContentBuilder = StringBuilder()

            fileContentBuilder.append(packageStatement(restController))
            fileContentBuilder.append(importsForDocumentationTestFile(projectState))

            val documentationTestFileName =
                RestDocsHelper.getDocumentationTestFileName(restController)

            documentationTestFile = PsiFileFactory.getInstance(currentProject)
                .createFileFromText(
                    documentationTestFileName,
                    JavaFileType.INSTANCE,
                    fileContentBuilder.toString()
                )

            val restDocumentationTestClass =
                generateRestDocumentationTestClass(
                    elementFactory,
                    documentationTestFileName,
                    restController,
                    projectState
                )

            documentationTestFile.add(restDocumentationTestClass)

            formatDocumentationTestFile(currentProject, documentationTestFile)

            val testSourceRootDirectory =
                PsiManager.getInstance(currentProject).findDirectory(testSourceRoot)!!

            val directory =
                createPackageDirectoriesIfNeeded(testSourceRootDirectory, restController)
            documentationTestFile = directory.add(documentationTestFile) as PsiFile
        }

        return documentationTestFile
    }

    private fun generateRestDocumentationTestClass(
        elementFactory: PsiElementFactory,
        classFileName: String,
        restController: PsiClass,
        projectState: SpringRestDocsGeneratorState
    ): PsiClass {
        logger.info("Generating documentation test class for %s".format(classFileName))
        val restDocumentationTestClass =
            elementFactory.createClass(classFileName.removeSuffix(".java"))

        PsiUtil.setModifierProperty(restDocumentationTestClass, PsiModifier.PACKAGE_LOCAL, true)
        for (annotation in projectState.restControllerDocumentationTestClassAnnotations.reversed()) {
            restDocumentationTestClass.modifierList?.addAnnotation(
                annotation.replace("^@+".toRegex(), "").trim()
            )
        }
        if (projectState.useDefaultClassAnnotation) {
            restDocumentationTestClass.modifierList?.addAnnotation("WebMvcTest(${restController.name}.class)")
            restDocumentationTestClass.modifierList?.addAnnotation("AutoConfigureRestDocs")
            restDocumentationTestClass.modifierList?.addAnnotation("ExtendWith({RestDocumentationExtension.class})")
        } else {
            restDocumentationTestClass.modifierList?.addAnnotation(
                projectState.customClassAnnotation
                    .replace("{rest-controller-name}", restController.name!!)
                    .replace("^@+".toRegex(), "")
            )
        }

        return restDocumentationTestClass
    }

    private fun packageStatement(
        restController: PsiClass,
    ): String {
        val packageName = RestDocsHelper.getPackageName(restController)

        val builder = StringBuilder()

        if (packageName.isNotEmpty()) {
            builder.appendLine("package $packageName;")
        }
        return builder.toString()
    }

    private fun importsForDocumentationTestFile(state: SpringRestDocsGeneratorState): String {

        with(StringBuilder()) {
            appendLine()
            appendLine("import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;")
            appendLine("import static org.springframework.restdocs.payload.PayloadDocumentation.*;")
            appendLine("import static org.springframework.restdocs.request.RequestDocumentation.*;")
            appendLine("import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;")
            appendLine("import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;")
            appendLine()
            appendLine("import org.junit.jupiter.api.Test;")
            appendLine()
            appendLine("import org.springframework.http.MediaType;")
            appendLine("import org.springframework.beans.factory.annotation.Autowired;")
            if (state.useDefaultClassAnnotation) {
                appendLine("import org.junit.jupiter.api.extension.ExtendWith;")
                appendLine("import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;")
                appendLine("import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;")
                appendLine("import org.springframework.restdocs.RestDocumentationExtension;")
            }
            appendLine()
            return toString()
        }

    }

    private fun formatDocumentationTestFile(
        currentProject: Project,
        documentationTestFile: PsiFile
    ) {
        val codeStyleManager = CodeStyleManager.getInstance(currentProject)
        codeStyleManager.reformat(documentationTestFile)
    }

    private fun createPackageDirectoriesIfNeeded(
        testSourceRootDirectory: PsiDirectory,
        selectedClass: PsiClass
    ): PsiDirectory {
        val packageName = RestDocsHelper.getPackageName(selectedClass)
        var directory = testSourceRootDirectory
        val packageNameParts = packageName.split(".").toList()
        for (packageNamePart in packageNameParts) {
            var subdirectory = directory.findSubdirectory(packageNamePart)
            if (subdirectory == null) {
                subdirectory = directory.createSubdirectory(packageNamePart)
            }
            directory = subdirectory
        }
        return directory
    }
}