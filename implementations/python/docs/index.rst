Prism Python API Reference
===========================

Welcome to the Prism Python API documentation. Prism is a versioned object synchronization protocol for real-time applications.

Installation
------------

.. code-block:: bash

   pip install prism

Quick Start
-----------

.. code-block:: python

   from prism import ObjectManager, PrismWebSocketHandler, MemoryStorage
   from fastapi import FastAPI, WebSocket

   # Setup storage and manager
   storage = MemoryStorage()
   manager = ObjectManager(storage)
   handler = PrismWebSocketHandler(manager)

   # Create FastAPI app
   app = FastAPI()

   @app.websocket("/ws")
   async def websocket_endpoint(websocket: WebSocket):
       await handler.handle_connection(websocket)

API Documentation
-----------------

.. toctree::
   :maxdepth: 2
   :caption: Core Modules

   api/core
   api/server
   api/storage
   api/filters

.. toctree::
   :maxdepth: 1
   :caption: Additional Resources

   GitHub Repository <https://github.com/rorygraves/prism>
   Protocol Specification <https://github.com/rorygraves/prism/blob/main/spec/protocol.md>

Indices and tables
==================

* :ref:`genindex`
* :ref:`modindex`
* :ref:`search`
