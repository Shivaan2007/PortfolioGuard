FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
RUN python -m textblob.download_corpora
COPY app.py .
EXPOSE 5002
CMD ["python", "app.py"]