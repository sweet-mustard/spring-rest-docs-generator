package be.sweetmustard.springrestdocsgenerator.settings

class SpringRestDocsGeneratorState {
    var restControllerDocumentationTestClassAnnotations: List<String> = ArrayList()
    var restControllerDocumentationTestMethodAnnotations: List<String> = ArrayList()
    var mockMvcAdditions: String = ""
    var useDefaultClassAnnotation: Boolean = true
    var customClassAnnotation: String = ""
}