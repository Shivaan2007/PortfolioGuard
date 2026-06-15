from flask import Flask, jsonify
import yfinance as yf
import datetime
import requests

app = Flask(__name__)

# Create a session that looks like a real browser to avoid rate limiting
def get_yf_ticker(ticker):
    session = requests.Session()
    session.headers.update({
        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.5',
    })
    return yf.Ticker(ticker, session=session)

@app.route('/price/<ticker>')
def get_price(ticker):
    try:
        stock = get_yf_ticker(ticker)
        end = datetime.datetime.now()
        start = end - datetime.timedelta(days=5)
        hist = stock.history(start=start, end=end, interval='1d')
        
        if not hist.empty:
            price = float(hist['Close'].iloc[-1])
            return jsonify({'ticker': ticker, 'price': round(price, 2), 'status': 'ok'})
        
        return jsonify({'error': 'No price data', 'ticker': ticker}), 500
    except Exception as e:
        return jsonify({'error': str(e), 'ticker': ticker}), 500

@app.route('/returns/<ticker>/<int:days>')
def get_returns(ticker, days):
    try:
        stock = get_yf_ticker(ticker)
        end = datetime.datetime.now()
        start = end - datetime.timedelta(days=days + 15)
        hist = stock.history(start=start, end=end, interval='1d')
        closes = hist['Close'].tolist()
        returns = [(closes[i] - closes[i-1]) / closes[i-1] for i in range(1, len(closes))]
        return jsonify({'ticker': ticker, 'returns': returns[-days:], 'status': 'ok'})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/health')
def health():
    return jsonify({'status': 'ok'})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5003)
