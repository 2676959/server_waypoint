package _959.server_waypoint.config;

import _959.server_waypoint.crossserver.CrossServerProtocol;
public class CommandPermission {
    int privateList = 0;
    int privateAdd = 0;
    int privateEdit = 0;
    int privateRemove = 0;
    int privateNavigate = 0;
    int privateTp = 2;
    int add = 0;
    int edit = 0;
    int remove = 0;
    int navigate = 0;
    int tp = 2;
    int reload = 2;
    int upload = 2;
    int uploadDelete = 4;
    int remoteList = CrossServerProtocol.REMOTE_LIST_DEFAULT_LEVEL;
    int remoteTp = CrossServerProtocol.REMOTE_TP_DEFAULT_LEVEL;

    public CommandPermission() {
    }

    public int add() {
        return this.add;
    }

    public int edit() {
        return this.edit;
    }

    public int remove() {
        return this.remove;
    }

    public int navigate() {
        return this.navigate;
    }

    public int tp() {
        return this.tp;
    }

    public int reload() {
        return this.reload;
    }

    public int upload() {
        return this.upload;
    }

    public int uploadDelete() {
        return this.uploadDelete;
    }

    public int remoteList() {
        return this.remoteList;
    }

    public int remoteTp() {
        return this.remoteTp;
    }

    public int privateList() {
        return this.privateList;
    }

    public int privateAdd() {
        return this.privateAdd;
    }

    public int privateEdit() {
        return this.privateEdit;
    }

    public int privateRemove() {
        return this.privateRemove;
    }

    public int privateNavigate() {
        return this.privateNavigate;
    }

    public int privateTp() {
        return this.privateTp;
    }

   @Override
   public String toString() {
      return "CommandPermission{" +
              "privateList=" + privateList +
              ", privateAdd=" + privateAdd +
              ", privateEdit=" + privateEdit +
              ", privateRemove=" + privateRemove +
              ", privateNavigate=" + privateNavigate +
              ", privateTp=" + privateTp +
              ", add=" + add +
              ", edit=" + edit +
              ", remove=" + remove +
              ", navigate=" + navigate +
              ", tp=" + tp +
              ", reload=" + reload +
              ", upload=" + upload +
              ", uploadDelete=" + uploadDelete +
              ", remoteList=" + remoteList +
              ", remoteTp=" + remoteTp +
              '}';
   }
}
