import kotlin.properties.Delegates
import kotlin.reflect.KProperty

class Container {
    private var name: String by Delegates.vetoable("Unknown") {
        property, oldValue, newValue ->
    }


}