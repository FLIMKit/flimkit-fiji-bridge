#@ Context context
import org.scijava.command.CommandService
def service = context.getService(CommandService.class)
def found = service.getCommands().findAll { it.getIdentifier().contains('flimkit') }
println("FLIMKIT_COMMANDS " + found.size())
found.each { println("  " + it.getMenuPath()) }
