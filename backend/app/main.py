from fastapi import FastAPI

app = FastAPI(
    title="Assistente Pessoal API",
    version="0.0.1",
)


@app.get("/")
async def root():
    return {
        "assistant": "online",
        "version": "0.0.1",
    }


@app.get("/health")
async def health():
    return {
        "status": "ok",
    }