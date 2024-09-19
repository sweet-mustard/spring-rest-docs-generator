package be.sweetmustard.springrestdocsgenerator

import org.assertj.swing.assertions.Assertions.assertThat
import org.junit.Test

class FieldDescriptionKtTest {
    
    @Test
    fun testMethodBodyGenerator() {

        val listOf = listOf(
            FieldDescription("", "id", "Id of xResponse"),
            FieldDescription("", "name", "Name of xResponse"),
            FieldDescription("", "yResponses[]", "Array of yResponses"),
            FieldDescription("yResponses[].", "id", "Id of the yResponse"),
            FieldDescription("yResponses[].", "name", "Name of yResponse"),
            FieldDescription("yResponses[].", "zResponse", "The zResponse of yResponse"),
            FieldDescription("yResponses[].zResponse.", "id", "Id of the zResponse"),
            FieldDescription("yResponses[].zResponse.", "name", "Name of the zResponse")
        )
        
        assertThat(buildResponseFieldsDescriptionString(listOf)).isEqualTo("""
            responseFields(
            fieldWithPath("id").description("Id of xResponse"),
            fieldWithPath("name").description("Name of xResponse"),
            fieldWithPath("yResponses[]").description("Array of yResponses"))
            .andWithPrefix("yResponses[].", 
            fieldWithPath("id").description("Id of the yResponse"),
            fieldWithPath("name").description("Name of yResponse"),
            fieldWithPath("zResponse").description("The zResponse of yResponse"))
            .andWithPrefix("yResponses[].zResponse.", 
            fieldWithPath("id").description("Id of the zResponse"),
            fieldWithPath("name").description("Name of the zResponse"))
        """.trimIndent())
    }
}