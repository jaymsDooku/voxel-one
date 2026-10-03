# Market economy implementation

Catalogue prices remain reference values. Transaction quotes use bounded square-root demand/supply pressure (0.25–4 times reference) and a seller inventory discount. Demand includes household hunger, unreserved construction needs, manufacturing inputs and missing productivity equipment. Stock reservations are excluded from supply. Quotes derive from existing saved simulation state and require no persistence or protocol migration.

Company and household material buyers compare eligible producer offers, buy from the cheapest first, and can acquire affordable partial quantities without overdrafts. Construction material ownership protections remain in place: shops and developers retain operating inventories, and family farms retain their construction stocks. Manufacturing recipes continue to require their specified inputs and equipment.

Households compare total meal costs among staffed open shops. Multiple portions can satisfy the same nutrition need, so low-nutrition foods remain candidates. Transactions transfer real inventory and money and consume the purchased food. Commercial restocking prioritises food value by nutritional content. Households compare affordable housing offers before committing to a purchase or lease.

New property sale/rental offers respond to city demand, capacity and local occupation; land acquisition prices respond to population and developed supply. Existing leases keep the negotiated amount. Owned farm accommodation remains rent-free.

Hourly labour quotes respond to the employer's positions, staffing and unemployment. Daily job reviews compare affordable wage offers with a 20% switching premium and preserve minimum operating crews and family-farm assignments. Mobile startup crews are limited to one worker, leaving construction labour available.

Regression coverage includes all configured products and building materials, construction and food demand, competing sellers, partial affordability, conservation, invalid transfers, meal portions, leases, labour demand, choosing cheaper housing in the real simulation, and quote/balance stability across restoration. Existing simulation tests retain their construction, production, ownership, nutrition, accounting and persistence assertions; fixed-price expectations now use actual negotiated quotes or paid plot prices.
