package be.sweetmustard.springrestdocsgenerator.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service
@State(
    name = "SpringRestDocsGeneratorSettings",
    storages = [Storage("spring-rest-docs-generator-settings.xml")]
)
class SpringRestDocsGeneratorSettings : PersistentStateComponent<SpringRestDocsGeneratorState> {

    var pluginState = SpringRestDocsGeneratorState()
    override fun getState(): SpringRestDocsGeneratorState {
        return pluginState
    }

    override fun loadState(state: SpringRestDocsGeneratorState) {
        this.pluginState = state
    }

    companion object {
        fun getInstance(project: Project): SpringRestDocsGeneratorSettings {
            return project.service<SpringRestDocsGeneratorSettings>()
        }
    }

}