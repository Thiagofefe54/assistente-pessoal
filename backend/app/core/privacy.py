"""Prevent HTTP caches from retaining personal API results, including errors."""
class PrivateApiResponses:
    def __init__(self,app):self.app=app

    async def __call__(self,scope,receive,send):
        if scope['type']!='http' or not scope.get('path','').startswith('/api/v1/'):
            return await self.app(scope,receive,send)
        async def private_send(event):
            if event['type']=='http.response.start':
                headers=[(key,value) for key,value in event.get('headers',[])
                         if key.lower() not in (b'cache-control',b'pragma',b'x-content-type-options')]
                event={**event,'headers':headers+[(b'cache-control',b'private, no-store'),
                    (b'pragma',b'no-cache'),(b'x-content-type-options',b'nosniff')]}
            await send(event)
        await self.app(scope,receive,private_send)
