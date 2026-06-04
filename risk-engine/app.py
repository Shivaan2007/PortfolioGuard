from flask import Flask, request, jsonify
from sklearn.ensemble import IsolationForest
import numpy as np

app = Flask(__name__)

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "risk-engine running"})

@app.route('/detect-anomalies', methods=['POST'])
def detect_anomalies():
    try:
        data = request.get_json()
        metrics_history = data.get('metrics_history', [])

        if len(metrics_history) < 10:
            return jsonify({
                "error": "Need at least 10 data points",
                "anomalies": [],
                "is_current_anomalous": False
            })

        features = np.array([[
            m['daily_return'],
            m['sharpe'],
            m['beta'],
            m['var95'],
            m['avg_correlation']
        ] for m in metrics_history])

        clf = IsolationForest(
            contamination=0.1,
            random_state=42,
            n_estimators=100
        )
        predictions = clf.fit_predict(features)
        scores = clf.score_samples(features)

        anomalies = []
        for i, pred in enumerate(predictions):
            if pred == -1:
                anomalies.append({
                    "index": i,
                    "metrics": metrics_history[i],
                    "anomaly_score": float(scores[i]),
                    "severity": "HIGH" if scores[i] < -0.1 else "MEDIUM"
                })

        is_current_anomalous = bool(predictions[-1] == -1)

        return jsonify({
            "total_points": len(metrics_history),
            "anomalies_detected": len(anomalies),
            "anomalies": anomalies,
            "is_current_anomalous": is_current_anomalous,
            "current_score": float(scores[-1])
        })

    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001)