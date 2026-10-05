# Market economy implementation

Catalogue prices remain reference values. Transaction quotes use bounded square-root demand/supply pressure (0.25–4 times reference) and a seller inventory discount. Demand includes household hunger, unreserved construction needs, manufacturing inputs and missing productivity equipment. Stock reservations are excluded from supply. Quotes derive from existing saved simulation state and require no persistence or protocol migration.

Company and household material buyers compare eligible producer offers, buy from the cheapest first, and can acquire affordable partial quantities without overdrafts. Construction material ownership protections remain in place: shops and developers retain operating inventories, and family farms retain their construction stocks. Manufacturing recipes continue to require their specified inputs and equipment.

Households compare total meal costs among staffed open shops. Multiple portions can satisfy the same nutrition need, so low-nutrition foods remain candidates. Transactions transfer real inventory and money and consume the purchased food. Commercial restocking prioritises food value by nutritional content. Households compare affordable housing offers before committing to a purchase or lease.

New property sale/rental offers respond to city demand, capacity and local occupation; land acquisition prices respond to population and developed supply. Existing leases keep the negotiated amount. Owned farm accommodation remains rent-free.

Hourly labour quotes respond to the employer's positions, staffing and unemployment. Daily job reviews compare affordable wage offers with a 20% switching premium and preserve minimum operating crews and family-farm assignments. Mobile startup crews are limited to one worker, leaving construction labour available.

Regression coverage includes all configured products and building materials, construction and food demand, competing sellers, partial affordability, conservation, invalid transfers, meal portions, leases, labour demand, choosing cheaper housing in the real simulation, and quote/balance stability across restoration. Existing simulation tests retain their construction, production, ownership, nutrition, accounting and persistence assertions; fixed-price expectations now use actual negotiated quotes or paid plot prices.

## Independent review corrections

Unfunded wage offers no longer retain minimum crews: workers can consider funded alternatives on any simulation tick without the normal switching premium. Automatic assignment checks that the hiring employer can fund its hourly offer, so it does not reclaim workers into unpaid positions on subsequent ticks. Funded minimum crews and family-farm assignments keep their existing protections.

Meal receipts now count the actual purchased portions in both lifetime and daily business sales. The regression fixture consumes four portions through the real simulation and reconciles recorded sales against shop stock and owned food inventory. Another regression checks minimum mine and shop crews before and after insolvency and their job choice over twelve subsequent simulation ticks.

## Exchange labour review correction

Exchange analysts and office support now have separate qualified labour markets. Their $2.7/hour and $1.8/hour reference wages use the same 0.25–4 bounded square-root demand/supply pressure as private labour. Demand counts exchange role positions; supply counts eligible unemployed and existing office staff. Farm workers stay excluded. Actual payroll still debits the public treasury and credits the worker.

Exchange offers participate in worker job reviews. Hiring checks treasury affordability and preserves three graduate analyst roles plus one non-graduate support role. Assignment compares funded alternatives instead of taking workers regardless of pay. Paid office workers can switch for an offer exceeding their current wage by 20%; unpaid office workers can seek any funded vacancy. A worker may stay assigned to an unfunded office when no funded vacancy exists, but receives no wage and cannot keep trading operational. Existing exchange operation and skill checks remain in force.

## Shop supplier-choice review correction

Agricultural shops compare actual eligible supplier offers per unit of nutrition across suitable products. They buy affordable whole portions, recheck offers after each purchase, and stop at the shared company inventory target. Seller depletion can make another product cheaper before the first product sells out. Catalogue and city reference-price ordering no longer decides which substitute to buy. Developer/shop exclusions and family-farm construction-stock protections are shared with normal material purchasing. Empty or unaffordable offers do not spend shop cash on unusable partial meals.
