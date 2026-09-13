FROM python:3.12-slim
WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Train at build time rather than at start-up. The image then carries a specific
# model, the container starts in a second instead of thirty, and two containers
# from the same image always give the same answer. Retraining means rebuilding,
# which is the correct amount of friction for changing what the model says.
RUN python train.py

RUN useradd --create-home --shell /bin/false app && chown -R app:app /app
USER app

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "::", "--port", "8000"]
