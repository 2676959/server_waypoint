package _959.server_waypoint.config;

public class CommandPermission {
    int add = 0;
    int edit = 0;
    int remove = 0;

    public int add() {
        return this.add;
    }

    public int edit() {
        return this.edit;
    }

    public int remove() {
        return this.remove;
    }

   @Override
   public String toString() {
      return "CommandPermission{" +
              "add=" + add +
              ", edit=" + edit +
              ", remove=" + remove +
              '}';
   }
}
