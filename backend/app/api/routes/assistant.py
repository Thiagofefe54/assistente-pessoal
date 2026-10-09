from uuid import UUID
from fastapi import APIRouter, Depends, Request
from backend.app.core.auth import current_user
from backend.app.api.routes.reports import ZoneRequest
from backend.app.core.assistant_panel import day_panel
from backend.app.core.poe_balance import balance

router = APIRouter(prefix='/assistant', tags=['Meu dia'])


@router.post('/day')
def day(body: ZoneRequest, request: Request, owner: UUID = Depends(current_user)):
    return day_panel(owner, request.headers['authorization'], body.timezone)


@router.get('/usage')
def usage(owner: UUID = Depends(current_user)):
    return balance()
