package _959.server_waypoint.config;

public class CommandPermission {
    int add = 0;
    int edit = 0;
    int remove = 0;
    int tp = 2;
    int reload = 2;

    public int add() {
        return this.add;
    }

    public int edit() {
        return this.edit;
    }

    public int remove() {
        return this.remove;
    }

    public int tp() {
        return this.tp;
    }

    public int reload() {
        return this.reload;
    }

   @Override
   public String toString() {
      return "CommandPermission{" +
              "add=" + add +
              ", edit=" + edit +
              ", remove=" + remove +
              ", tp=" + tp +
              ", reload=" + reload +
              '}';
   }
}
