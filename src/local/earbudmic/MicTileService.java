package local.earbudmic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile that turns {@link ScoService} on and off. */
public final class MicTileService extends TileService {
    /** The instance System UI is currently showing, if any. Main thread only. */
    private static MicTileService listening;

    @Override
    public void onStartListening() {
        listening = this;
        update();
    }

    @Override
    public void onStopListening() {
        if (listening == this) {
            listening = null;
        }
    }

    @Override
    public void onClick() {
        Intent service = new Intent(this, ScoService.class);
        if (ScoService.running) {
            stopService(service);
        } else {
            startForegroundService(service);
        }
    }

    /** Called by {@link ScoService} on the main thread whenever it turns on or off. */
    static void refresh(Context context) {
        MicTileService tile = listening;
        if (tile != null) {
            tile.update();
        } else {
            requestListeningState(context, new ComponentName(context, MicTileService.class));
        }
    }

    private void update() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        boolean on = ScoService.running;
        boolean headset = on || ScoService.findSco(getSystemService(AudioManager.class)) != null;
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(headset ? null : "No headset");
        tile.updateTile();
    }
}
