"""Resolve explicit relative deadlines with the request's original clock, not inference."""
import re
import unicodedata
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo


def plain(text):
    return ''.join(c for c in unicodedata.normalize('NFKD', text.lower()) if not unicodedata.combining(c))


def relative_deadline(message, now, timezone):
    message = re.sub(r'"[^"\n]*"|“[^”\n]*”|\'[^\'\n]*\'', '', message)
    match = re.search(r'\b(?:daqui\s+(?:a\s+)?|em\s+)(\d{1,5}|um|uma|dois|duas|dez|quinze|meia)\s*(minutos?|mins?|horas?|dias?)\b', plain(message))
    if not match:
        return None
    number = match[1]
    value = float(number) if number.isdigit() else {'um':1,'uma':1,'dois':2,'duas':2,'dez':10,'quinze':15,'meia':.5}[number]
    minutes = value * (1440 if match[2].startswith('dia') else 60 if match[2].startswith('hora') else 1)
    if not 1 <= minutes <= 525600:
        raise ValueError('Prazo deve ficar entre um minuto e um ano.')
    anchor = datetime.fromisoformat(now).astimezone(ZoneInfo(timezone))
    # Round upward so a minute-resolution reminder never fires earlier than requested.
    due = anchor + timedelta(minutes=minutes)
    if due.second or due.microsecond:
        due = due.replace(second=0, microsecond=0) + timedelta(minutes=1)
    return due.date().isoformat(), due.strftime('%H:%M')


def simple_relative_title(message):
    # Only a complete imperative reminder is resolved without the model.
    match = re.fullmatch(r'\s*(?:koi[,!]?\s*)?(?:crie|cria|adicione|adiciona)\s+(?:uma\s+)?(?:tarefa|lembrete)\s+(?:me\s+lembrando\s+de\s+|(?:chamad[oa]|para|de)\s+)?(.+?)\s+(?:daqui\s+(?:a\s+)?|em\s+)(?:\d{1,5}|um|uma|dois|duas|dez|quinze|meia)\s*(?:minutos?|mins?|horas?|dias?)[.!]?\s*', message, re.I)
    if not match:
        match = re.fullmatch(r'\s*(?:koi[,!]?\s*)?(?:me\s+lembre|me\s+lembra|lembre-me)\s+de\s+(.+?)\s+(?:daqui\s+(?:a\s+)?|em\s+)(?:\d{1,5}|um|uma|dois|duas|dez|quinze|meia)\s*(?:minutos?|mins?|horas?|dias?)[.!]?\s*', message, re.I)
    if not match:
        return None
    title = match[1].strip(' \"“”')
    if not 1 <= len(title) <= 160 or re.search(r'\b(?:e depois|mas|se|nao|não|conclua|arquive|apague)\b', plain(title)):
        return None
    return title
