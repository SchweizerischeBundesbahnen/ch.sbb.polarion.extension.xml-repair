interface ScanningIndicatorProps {
  elapsed: number;
  progress: string | null;
  stopping: boolean;
  canStop: boolean;
  onStop: () => void;
}

export default function ScanningIndicator({ elapsed, progress, stopping, canStop, onStop }: ScanningIndicatorProps) {
  return (
    <div className="scanning-indicator">
      <div className="scanning-status">
        <span className="spinner" />
        <span>
          {stopping ? 'Stopping' : 'Scanning'}... {(elapsed / 1000).toFixed(1)}s
        </span>
        {progress && <span className="scanning-progress">{progress}</span>}
      </div>
      <button
        type="button"
        className="btn btn-stop"
        onClick={onStop}
        disabled={!canStop || stopping}
        title="Stop the scan and show the items scanned so far"
      >
        Stop
      </button>
    </div>
  );
}
