from flask import Flask, request, jsonify
from textblob import TextBlob
import requests
import os

app = Flask(__name__)

NEWS_API_KEY = os.getenv('NEWS_API_KEY', '')
NEWS_API_URL = "https://newsapi.org/v2/everything"

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "sentiment-service running"})

@app.route('/sentiment/<ticker>', methods=['GET'])
def get_sentiment(ticker):
    try:
        params = {
            'q': ticker + ' stock',
            'sortBy': 'publishedAt',
            'pageSize': 20,
            'apiKey': NEWS_API_KEY,
            'language': 'en'
        }

        response = requests.get(NEWS_API_URL, params=params)
        articles = response.json().get('articles', [])

        if not articles:
            return jsonify({
                'ticker': ticker,
                'score': 0.0,
                'label': 'NEUTRAL',
                'articleCount': 0,
                'headlines': []
            })

        scores = []
        headlines = []

        for article in articles:
            title = article.get('title', '')
            description = article.get('description', '')
            text = title + ' ' + (description or '')

            blob = TextBlob(text)
            scores.append(blob.sentiment.polarity)
            if title:
                headlines.append(title)

        avg_score = sum(scores) / len(scores)

        if avg_score > 0.1:
            label = 'POSITIVE'
        elif avg_score < -0.1:
            label = 'NEGATIVE'
        else:
            label = 'NEUTRAL'

        return jsonify({
            'ticker': ticker,
            'score': round(avg_score, 4),
            'label': label,
            'articleCount': len(articles),
            'headlines': headlines[:5]
        })

    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5002)