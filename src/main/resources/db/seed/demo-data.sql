-- Local/development sandbox fixtures. All demo users log in with admin@123.
-- Only the BCrypt hash is stored in password_hash.
-- Run the whole file in one transaction. A seed marker prevents repeat inserts.
-- Asset paths and bank references are mock fixtures, not uploaded files or real banks.
SET LOCAL search_path TO public, pg_catalog;

DO $seed$
DECLARE
    u INTEGER[] := ARRAY[]::INTEGER[];
    addr INTEGER[] := ARRAY[]::INTEGER[];
    bank INTEGER[] := ARRAY[]::INTEGER[];
    wallet INTEGER[] := ARRAY[]::INTEGER[];
    cart INTEGER[] := ARRAY[]::INTEGER[];
    shop INTEGER[] := ARRAY[]::INTEGER[];
    subscription INTEGER[] := ARRAY[]::INTEGER[];
    product INTEGER[] := ARRAY[]::INTEGER[];
    variant INTEGER[] := ARRAY[]::INTEGER[];
    model INTEGER[] := ARRAY[]::INTEGER[];
    version INTEGER[] := ARRAY[]::INTEGER[];
    sample_emails TEXT[] := ARRAY[
        'demo.admin@3dworld.test', 'demo.staff@3dworld.test',
        'demo.designer01@3dworld.test', 'demo.designer02@3dworld.test',
        'demo.seller01@3dworld.test', 'demo.seller02@3dworld.test',
        'demo.customer01@3dworld.test', 'demo.customer02@3dworld.test',
        'demo.locked@3dworld.test', 'demo.banned@3dworld.test'
    ];
    sample_names TEXT[] := ARRAY[
        'Nguyễn Minh Admin', 'Trần Thảo Print Staff', 'Lê Bảo Designer', 'Phạm Linh Designer',
        'Vũ Nam Seller', 'Đỗ Hạnh Seller', 'Nguyễn An Customer', 'Trần Bình Customer',
        'Lê Cường Locked', 'Phạm Dũng Banned'
    ];
    password_bcrypt TEXT := '$2a$10$Y4VRNeJCERgli8hbWzcrNOvN6pNebw.jN481b0l9kEp4cGOqPbyrC';
    i INTEGER;
    row_id INTEGER;
    tx_id INTEGER;
    ai_id INTEGER;
    order_paid INTEGER;
    order_pending INTEGER;
    sample_earning_id INTEGER;
    offer_id INTEGER;
    plan_id INTEGER;
    contract_id INTEGER;
    sample_material_id INTEGER;
    print_id INTEGER;
    print_item_id INTEGER;
    quote_id INTEGER;
    balance NUMERIC(18,2);
    source_time TIMESTAMPTZ;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('3dworld-demo-seed-v1'));
    IF EXISTS (SELECT 1 FROM platform_configs WHERE config_key = 'demo.seed.v1') THEN
        RAISE NOTICE 'Demo data already seeded; no changes made.';
        RETURN;
    END IF;

    INSERT INTO roles(role_code, role_name, is_internal) VALUES
        ('CUSTOMER', 'Customer', false), ('SELLER', 'Seller', false),
        ('DESIGNER', 'Designer', false), ('PRINT_STAFF', 'Print Staff', true),
        ('ADMIN', 'Administrator', true)
    ON CONFLICT(role_code) DO NOTHING;

    -- Ten accounts; only demo-prefixed accounts are affected by the upsert.
    FOR i IN 1..10 LOOP
        INSERT INTO users(email, password_hash, full_name, phone, email_verified, phone_verified,
                          account_status, ai_credits_balance, created_at)
        VALUES(sample_emails[i], password_bcrypt, sample_names[i], '09070000' || lpad(i::TEXT, 2, '0'),
               i <> 8, i NOT IN (8,9,10),
               CASE WHEN i = 9 THEN 'LOCKED' WHEN i = 10 THEN 'BANNED' ELSE 'ACTIVE' END,
               CASE WHEN i = 4 THEN 49 ELSE 50 END, now() - interval '45 days')
        ON CONFLICT(email) DO UPDATE SET
            password_hash = EXCLUDED.password_hash,
            full_name = EXCLUDED.full_name, phone = EXCLUDED.phone,
            email_verified = EXCLUDED.email_verified, phone_verified = EXCLUDED.phone_verified,
            account_status = EXCLUDED.account_status, ai_credits_balance = EXCLUDED.ai_credits_balance
        RETURNING user_id INTO row_id;
        u := array_append(u, row_id);
    END LOOP;

    INSERT INTO user_roles(user_id, role_id, approval_status, granted_by_user_id, granted_at)
    SELECT id, r.role_id, 'ACTIVE', u[1], now() - interval '40 days'
    FROM unnest(u) AS account(id) CROSS JOIN roles r WHERE r.role_code = 'CUSTOMER'
    ON CONFLICT(user_id, role_id) DO UPDATE SET approval_status = 'ACTIVE';

    INSERT INTO user_roles(user_id, role_id, approval_status, granted_by_user_id, granted_at)
    SELECT access.user_id, r.role_id, 'ACTIVE', u[1], now() - interval '40 days'
    FROM (VALUES
        (u[1], 'ADMIN'), (u[2], 'PRINT_STAFF'),
        (u[3], 'DESIGNER'), (u[4], 'DESIGNER'),
        (u[5], 'SELLER'), (u[6], 'SELLER'), (u[3], 'SELLER')
    ) AS access(user_id, role_code)
    JOIN roles r ON r.role_code = access.role_code
    ON CONFLICT(user_id, role_id) DO UPDATE SET
        approval_status = 'ACTIVE', granted_by_user_id = u[1], granted_at = now();

    -- Each account gets its own address, masked sandbox bank, cart and funded wallet.
    FOR i IN 1..10 LOOP
        INSERT INTO user_addresses(user_id, recipient_name, phone, address_line, city, is_default, created_at)
        VALUES(u[i], sample_names[i], '09070000' || lpad(i::TEXT, 2, '0'),
               'DEMO: ' || i || ' Nguyễn Văn Cừ, Quận 5', 'Hồ Chí Minh',
               NOT EXISTS(SELECT 1 FROM user_addresses WHERE user_id = u[i] AND is_default),
               now() - interval '40 days')
        RETURNING address_id INTO row_id;
        addr := array_append(addr, row_id);

        INSERT INTO user_bank_accounts(user_id, bank_code, bank_name, account_holder,
            account_number_token, account_number_masked, verification_status, is_default, verified_at, created_at)
        VALUES(u[i], 'DEMO_BANK', 'Demo Sandbox Bank', sample_names[i],
            'DEMO_VAULT_REFERENCE_' || i, '******' || lpad((1000+i)::TEXT, 4, '0'),
            CASE WHEN i IN (1,2,3,4,5,6,7) THEN 'VERIFIED' ELSE 'PENDING' END,
            NOT EXISTS(SELECT 1 FROM user_bank_accounts WHERE user_id = u[i] AND is_default AND is_active),
            CASE WHEN i IN (1,2,3,4,5,6,7) THEN now() - interval '35 days' ELSE NULL END,
            now() - interval '40 days')
        RETURNING bank_account_id INTO row_id;
        bank := array_append(bank, row_id);

        INSERT INTO carts(user_id) VALUES(u[i]) ON CONFLICT(user_id) DO NOTHING;
        SELECT cart_id INTO row_id FROM carts WHERE user_id = u[i];
        cart := array_append(cart, row_id);

        INSERT INTO wallets(user_id) VALUES(u[i]) ON CONFLICT(user_id) DO NOTHING;
        SELECT wallet_id INTO row_id FROM wallets WHERE user_id = u[i];
        wallet := array_append(wallet, row_id);

        INSERT INTO transactions(transaction_type, purpose, operation_key, wallet_id, payer_user_id,
            payment_stage, settlement_mode, amount, status, requested_by_user_id, created_at, completed_at)
        VALUES('TOPUP', 'WALLET', 'DEMO-SEED-V1-TOPUP-' || i, wallet[i], u[i],
            'TOPUP', 'PLATFORM', 1000000, 'SUCCESS', u[i], now()-interval '10 days', now()-interval '10 days')
        RETURNING transaction_id INTO tx_id;

        INSERT INTO transaction_attempts(transaction_id, attempt_number, idempotency_key, payment_method,
            provider_name, external_reference, attempt_status, created_at, completed_at)
        VALUES(tx_id, 1, 'DEMO-SEED-V1-TOPUP-ATTEMPT-' || i, 'GATEWAY', 'SANDBOX',
            'DEMO-TOPUP-' || i, 'SUCCESS', now()-interval '10 days', now()-interval '10 days');

        UPDATE wallets SET available_balance = available_balance + 1000000, updated_at = now()
        WHERE wallet_id = wallet[i] RETURNING available_balance INTO balance;
        INSERT INTO wallet_ledgers(wallet_id, entry_type, balance_bucket, signed_amount, balance_after,
            transaction_id, idempotency_key, created_at)
        VALUES(wallet[i], 'TOPUP', 'AVAILABLE', 1000000, balance, tx_id,
            'DEMO-SEED-V1-TOPUP-LEDGER-' || i, now()-interval '10 days');

        INSERT INTO notifications(recipient_user_id, event_type, title, content, related_entity_type, related_entity_id)
        VALUES(u[i], 'DEMO_SEED', 'Tài khoản demo đã sẵn sàng',
            'Dữ liệu sandbox để kiểm tra API. Bank và asset references là mock.', 'USER', u[i]);
        INSERT INTO audit_logs(actor_user_id, action_type, entity_type, entity_id, reason, metadata)
        VALUES(u[1], 'DEMO_ACCOUNT_SEEDED', 'USER', u[i], 'Development sandbox fixture', '{"seed":"demo-v1"}');
    END LOOP;

    -- Two sellers and one designer-owned storefront. Seller 02 has expired subscription.
    FOR i IN 1..3 LOOP
        row_id := CASE WHEN i = 1 THEN u[5] WHEN i = 2 THEN u[6] ELSE u[3] END;
        INSERT INTO storefronts(owner_user_id, storefront_name, description, seller_verification_status,
            verified_at, status, expires_at, created_at)
        VALUES(row_id, CASE WHEN i=1 THEN 'Demo Print Shop' WHEN i=2 THEN 'Demo Expired Shop' ELSE 'Demo Designer Studio' END,
            'DEMO: cửa hàng sandbox', 'VERIFIED', now()-interval '34 days', 'ACTIVE',
            CASE WHEN i=2 THEN now()-interval '1 day' ELSE now()+interval '29 days' END, now()-interval '35 days')
        ON CONFLICT(owner_user_id) DO UPDATE SET storefront_name=EXCLUDED.storefront_name,
            description=EXCLUDED.description, seller_verification_status=EXCLUDED.seller_verification_status,
            verified_at=EXCLUDED.verified_at, status=EXCLUDED.status, expires_at=EXCLUDED.expires_at
        RETURNING storefront_id INTO row_id;
        shop := array_append(shop, row_id);
        source_time := CASE WHEN i=2 THEN now()-interval '31 days' ELSE now()-interval '1 day' END;
        INSERT INTO storefront_subscriptions(storefront_id, fee_snapshot, start_at, end_at, status, paid_at, created_at)
        VALUES(shop[i], 199000, source_time, source_time+interval '30 days',
            CASE WHEN i=2 THEN 'EXPIRED' ELSE 'ACTIVE' END, source_time, source_time)
        RETURNING subscription_id INTO row_id;
        subscription := array_append(subscription, row_id);
        INSERT INTO transactions(transaction_type, purpose, operation_key, storefront_subscription_id,
            payer_user_id, payment_stage, settlement_mode, amount, status, created_at, completed_at)
        VALUES('PAYMENT','SUBSCRIPTION','DEMO-SEED-V1-SUB-' || i, subscription[i],
            CASE WHEN i=1 THEN u[5] WHEN i=2 THEN u[6] ELSE u[3] END,
            'SUBSCRIPTION','PLATFORM',199000,'SUCCESS',source_time,source_time)
        RETURNING transaction_id INTO tx_id;
        INSERT INTO transaction_attempts(transaction_id, attempt_number, idempotency_key, payment_method,
            provider_name, external_reference, attempt_status, created_at, completed_at)
        VALUES(tx_id,1,'DEMO-SEED-V1-SUB-ATTEMPT-' || i,'GATEWAY','SANDBOX','DEMO-SUB-' || i,
            'SUCCESS',source_time,source_time);
    END LOOP;

    -- Model library and an AI-generated output linked to its request.
    INSERT INTO models_3d(owner_user_id, model_name, description, model_type, origin_type, visibility, created_at)
    VALUES(u[3], 'Demo Dragon Model', 'DEMO: mock storage files', 'WEB_EDITOR', 'UPLOAD', 'PUBLIC', now()-interval '7 days')
    RETURNING model_id INTO row_id;
    model := array_append(model, row_id);
    INSERT INTO model_versions(model_id, created_by_user_id, version_number, source_type, change_note, created_at)
    VALUES(model[1], u[3], 1, 'UPLOAD', 'Demo uploaded model', now()-interval '7 days')
    RETURNING model_version_id INTO row_id;
    version := array_append(version, row_id);
    INSERT INTO model_files(model_version_id, file_role, file_format, storage_key, mime_type, is_primary)
    VALUES(version[1], 'VIEWABLE_MODEL', 'GLB', 'demo/models/dragon.glb', 'model/gltf-binary', true),
          (version[1], 'PRINT_MODEL', 'STL', 'demo/models/dragon.stl', 'model/stl', false);

    INSERT INTO models_3d(owner_user_id, model_name, description, model_type, origin_type, visibility, created_at)
    VALUES(u[4], 'Demo AI Robot', 'DEMO: mock AI output', 'AI_GENERATOR', 'AI', 'PRIVATE', now()-interval '6 days')
    RETURNING model_id INTO row_id;
    model := array_append(model, row_id);
    INSERT INTO ai_requests(user_id, model_id, request_type, action_type, prompt, provider_name,
        provider_job_reference, credits_used, status, created_at, completed_at)
    VALUES(u[4],model[2],'TEXT_TO_3D','GENERATE','Demo miniature robot','MOCK_AI','DEMO-AI-JOB-001',1,
        'SUCCESS',now()-interval '6 days',now()-interval '6 days'+interval '5 minutes')
    RETURNING ai_request_id INTO ai_id;
    INSERT INTO model_versions(model_id, ai_request_id, created_by_user_id, version_number, source_type, created_at)
    VALUES(model[2],ai_id,u[4],1,'AI_GENERATED',now()-interval '6 days'+interval '5 minutes')
    RETURNING model_version_id INTO row_id;
    version := array_append(version,row_id);
    INSERT INTO model_files(model_version_id, file_role, file_format, storage_key, mime_type, is_primary)
    VALUES(version[2],'VIEWABLE_MODEL','GLB','demo/models/robot.glb','model/gltf-binary',true);

    -- Catalog products and cart items.
    FOR i IN 1..3 LOOP
        INSERT INTO products(storefront_id, title, description, category, status, published_at, created_at)
        VALUES(CASE WHEN i=3 THEN shop[2] ELSE shop[1] END,
            CASE WHEN i=1 THEN 'Demo Dragon Figurine' WHEN i=2 THEN 'Demo Desk Stand' ELSE 'Demo Robot Figurine' END,
            'DEMO: sản phẩm in 3D', 'FIGURINE', 'PUBLISHED', now()-interval '5 days',now()-interval '6 days')
        RETURNING product_id INTO row_id;
        product := array_append(product,row_id);
        INSERT INTO product_variants(product_id, sku, variant_attributes, unit_price, stock_quantity, reserved_quantity)
        VALUES(product[i], 'DEMO-SKU-' || i, '{"material":"PLA","color":"white"}',
            CASE WHEN i=1 THEN 299000 WHEN i=2 THEN 149000 ELSE 199000 END,
            CASE WHEN i=1 THEN 49 WHEN i=2 THEN 30 ELSE 20 END, CASE WHEN i=3 THEN 2 ELSE 0 END)
        RETURNING variant_id INTO row_id;
        variant := array_append(variant,row_id);
        INSERT INTO product_media(product_id, media_type, media_url)
        VALUES(product[i], 'IMAGE', '/demo/assets/product-' || i || '.png');
    END LOOP;
    INSERT INTO cart_items(cart_id, variant_id, quantity) VALUES(cart[7],variant[2],2),(cart[8],variant[3],1);

    INSERT INTO physical_orders(buyer_user_id, storefront_id, shipping_address_id, shipping_address_snapshot,
        checkout_group_id, subtotal, shipping_fee, total_amount, order_status,
        delivered_at, received_at, refund_deadline_at, created_at)
    VALUES(u[7],shop[1],addr[7],'{"recipient":"Nguyễn An Customer","phone":"0907000007","address":"7 Nguyễn Văn Cừ, Quận 5, Hồ Chí Minh"}',
        '3d000000-0000-4000-8000-000000000001',299000,25000,324000,'COMPLETED',
        now()-interval '2 days',now()-interval '1 day',now()+interval '5 days',now()-interval '5 days')
    RETURNING physical_order_id INTO order_paid;
    INSERT INTO physical_order_items(physical_order_id,variant_id,sku_snapshot,variant_snapshot,quantity,unit_price_snapshot,line_total)
    VALUES(order_paid,variant[1],'DEMO-SKU-1','{"material":"PLA","color":"white"}',1,299000,299000);
    INSERT INTO shipments(physical_order_id,carrier_name,tracking_number,status,shipped_at,delivered_at,created_at)
    VALUES(order_paid,'MOCK_CARRIER','DEMO-PHYSICAL-001','DELIVERED',now()-interval '4 days',now()-interval '2 days',now()-interval '4 days');
    INSERT INTO transactions(transaction_type,purpose,operation_key,physical_order_id,payer_user_id,payee_user_id,
        payment_stage,settlement_mode,amount,status,created_at,completed_at)
    VALUES('PAYMENT','PHYSICAL_ORDER','DEMO-SEED-V1-ORDER-PAID',order_paid,u[7],u[5],
        'FULL','PLATFORM',324000,'SUCCESS',now()-interval '5 days',now()-interval '4 days')
    RETURNING transaction_id INTO tx_id;
    INSERT INTO transaction_attempts(transaction_id,attempt_number,idempotency_key,payment_method,provider_name,
        external_reference,attempt_status,created_at,completed_at)
    VALUES(tx_id,1,'DEMO-SEED-V1-ORDER-PAID-ATTEMPT','GATEWAY','SANDBOX','DEMO-ORDER-PAID','SUCCESS',now()-interval '5 days',now()-interval '4 days');

    -- The seller earning is pending, not yet released into the available bucket.
    INSERT INTO seller_earnings(physical_order_id,seller_user_id,wallet_id,gross_amount,commission_rate_snapshot,
        commission_amount,net_amount,earning_status,platform_funds_received_at,pending_posted_at,release_eligible_at,created_at)
    VALUES(order_paid,u[5],wallet[5],299000,0.10,29900,269100,'PENDING',now()-interval '4 days',
        now()-interval '3 days',now()+interval '5 days',now()-interval '4 days')
    RETURNING earning_id INTO sample_earning_id;
    UPDATE wallets SET pending_balance=pending_balance+269100,updated_at=now()
    WHERE wallet_id=wallet[5] RETURNING pending_balance INTO balance;
    INSERT INTO wallet_ledgers(wallet_id,entry_type,balance_bucket,signed_amount,balance_after,seller_earning_id,idempotency_key,created_at)
    VALUES(wallet[5],'SELLER_EARNING','PENDING',269100,balance,sample_earning_id,'DEMO-SEED-V1-EARNING-LEDGER',now()-interval '3 days');

    INSERT INTO physical_orders(buyer_user_id,storefront_id,shipping_address_id,shipping_address_snapshot,
        checkout_group_id,subtotal,shipping_fee,total_amount,order_status,created_at)
    VALUES(u[8],shop[2],addr[8],'{"recipient":"Trần Bình Customer","phone":"0907000008","address":"8 Nguyễn Văn Cừ, Quận 5, Hồ Chí Minh"}',
        '3d000000-0000-4000-8000-000000000002',398000,30000,428000,'CREATED',now()-interval '2 days')
    RETURNING physical_order_id INTO order_pending;
    INSERT INTO physical_order_items(physical_order_id,variant_id,sku_snapshot,variant_snapshot,quantity,unit_price_snapshot,line_total)
    VALUES(order_pending,variant[3],'DEMO-SKU-3','{"material":"PLA","color":"white"}',2,199000,398000);
    INSERT INTO transactions(transaction_type,purpose,operation_key,physical_order_id,payer_user_id,payee_user_id,
        payment_stage,settlement_mode,amount,status,created_at)
    VALUES('PAYMENT','PHYSICAL_ORDER','DEMO-SEED-V1-ORDER-PENDING',order_pending,u[8],u[6],
        'FULL','PLATFORM',428000,'PENDING',now()-interval '2 days')
    RETURNING transaction_id INTO tx_id;
    INSERT INTO transaction_attempts(transaction_id,attempt_number,idempotency_key,payment_method,provider_name,
        external_reference,attempt_status,review_reason,created_at,completed_at)
    VALUES(tx_id,1,'DEMO-SEED-V1-ORDER-FAILED-ATTEMPT','GATEWAY','SANDBOX','DEMO-ORDER-FAILED','FAILED',
        'Demo provider timeout',now()-interval '2 days',now()-interval '2 days'+interval '1 minute'),
        (tx_id,2,'DEMO-SEED-V1-ORDER-RETRY-ATTEMPT','GATEWAY','SANDBOX','DEMO-ORDER-RETRY','PENDING',
        NULL,now()-interval '2 days'+interval '5 minutes',NULL);

    -- Rental offer belongs to the designer's storefront; snapshots match the plan.
    INSERT INTO rental_offers(storefront_id,model_version_id,title,status,rights_declaration_accepted_at,created_at)
    VALUES(shop[3],version[1],'Demo Dragon License','PUBLISHED',now()-interval '5 days',now()-interval '5 days')
    RETURNING rental_offer_id INTO offer_id;
    INSERT INTO rental_plans(rental_offer_id,duration_days,price,allow_view,allow_download,allow_print,license_terms,terms_version)
    VALUES(offer_id,7,99000,true,false,true,'Demo sandbox license: personal viewing and printing only.','DEMO-v1')
    RETURNING rental_plan_id INTO plan_id;
    INSERT INTO rental_contracts(rental_offer_id,rental_plan_id,model_version_id,renter_user_id,designer_user_id,
        price_snapshot,allow_view_snapshot,allow_download_snapshot,allow_print_snapshot,license_terms_snapshot,
        license_accepted_at,status,start_at,end_at,created_at)
    VALUES(offer_id,plan_id,version[1],u[7],u[3],99000,true,false,true,
        'Demo sandbox license: personal viewing and printing only.',now()-interval '3 days',
        'ACTIVE',now()-interval '3 days',now()+interval '4 days',now()-interval '3 days')
    RETURNING rental_contract_id INTO contract_id;
    INSERT INTO transactions(transaction_type,purpose,operation_key,rental_contract_id,payer_user_id,payee_user_id,
        payee_bank_account_id,payment_stage,settlement_mode,amount,status,created_at,completed_at)
    VALUES('PAYMENT','RENTAL','DEMO-SEED-V1-RENTAL',contract_id,u[7],u[3],bank[3],
        'FULL','DIRECT_TO_DESIGNER',99000,'SUCCESS',now()-interval '3 days',now()-interval '3 days')
    RETURNING transaction_id INTO tx_id;
    INSERT INTO transaction_attempts(transaction_id,attempt_number,idempotency_key,payment_method,provider_name,
        external_reference,bank_destination_snapshot,attempt_status,created_at,completed_at)
    VALUES(tx_id,1,'DEMO-SEED-V1-RENTAL-ATTEMPT','BANK_TRANSFER','SANDBOX','DEMO-RENTAL',
        '{"bank":"Demo Sandbox Bank","account":"******1003"}','SUCCESS',now()-interval '3 days',now()-interval '3 days');

    -- Printing quote: deposit paid, production completed, remaining pending before shipping.
    INSERT INTO print_materials(material_name,technology_code,unit,estimated_unit_cost)
    VALUES('Demo PLA','FDM','g',300) RETURNING material_id INTO sample_material_id;
    INSERT INTO print_requests(customer_user_id,shipping_address_id,shipping_address_snapshot,status,customer_notes,locked_at,created_at)
    VALUES(u[7],addr[7],'{"recipient":"Nguyễn An Customer","phone":"0907000007","address":"7 Nguyễn Văn Cừ, Quận 5, Hồ Chí Minh"}',
        'READY_FOR_SHIPMENT','DEMO: đã in 2 mô hình trắng',now()-interval '20 hours',now()-interval '2 days')
    RETURNING print_request_id INTO print_id;
    INSERT INTO print_request_items(print_request_id,model_version_id,material_id,color,quality,infill_percent,
        width_mm,height_mm,depth_mm,quantity,production_status)
    VALUES(print_id,version[1],sample_material_id,'white','STANDARD',20,80,80,100,2,'COMPLETED')
    RETURNING print_request_item_id INTO print_item_id;
    INSERT INTO print_quotes(print_request_id,quoted_by_user_id,version_number,total_amount,deposit_amount,
        remaining_amount,eta_at,expires_at,quote_status,issued_at,accepted_at)
    VALUES(print_id,u[2],1,200000,100000,100000,now()+interval '3 days',now()+interval '5 days',
        'ACCEPTED',now()-interval '1 day',now()-interval '20 hours')
    RETURNING print_quote_id INTO quote_id;
    INSERT INTO print_quote_items(print_quote_id,print_request_item_id,file_reference_snapshot,specification_snapshot,
        quantity_snapshot,material_cost_snapshot,labor_cost_snapshot,option_fee_snapshot,line_total)
    VALUES(quote_id,print_item_id,'demo/models/dragon.stl',
        '{"material":"PLA","color":"white","infill":20,"dimensionsMm":[80,80,100]}',2,100000,80000,20000,200000);
    INSERT INTO shipments(print_request_id,carrier_name,tracking_number,status)
    VALUES(print_id,'MOCK_CARRIER','DEMO-PRINT-001','PENDING');
    FOR i IN 1..2 LOOP
        INSERT INTO transactions(transaction_type,purpose,operation_key,print_request_id,print_quote_id,
            payer_user_id,payment_stage,settlement_mode,amount,status,created_at,completed_at)
        VALUES('PAYMENT','POD','DEMO-SEED-V1-POD-' || i,print_id,quote_id,u[7],
            CASE WHEN i=1 THEN 'DEPOSIT' ELSE 'REMAINING' END,'PLATFORM',100000,
            CASE WHEN i=1 THEN 'SUCCESS' ELSE 'PENDING' END,now()-interval '18 hours',
            CASE WHEN i=1 THEN now()-interval '18 hours' ELSE NULL END)
        RETURNING transaction_id INTO tx_id;
        INSERT INTO transaction_attempts(transaction_id,attempt_number,idempotency_key,payment_method,provider_name,
            external_reference,attempt_status,created_at,completed_at)
        VALUES(tx_id,1,'DEMO-SEED-V1-POD-ATTEMPT-' || i,'GATEWAY','SANDBOX','DEMO-POD-' || i,
            CASE WHEN i=1 THEN 'SUCCESS' ELSE 'PENDING' END,now()-interval '18 hours',
            CASE WHEN i=1 THEN now()-interval '18 hours' ELSE NULL END);
    END LOOP;

    INSERT INTO reports(reporter_user_id,product_id,report_reason,description,status)
    VALUES(u[8],product[1],'OTHER','DEMO: cần kiểm tra mô tả sản phẩm','OPEN');
    INSERT INTO reports(reporter_user_id,rental_offer_id,report_reason,description,status,handled_by_user_id,decision_reason,resolved_at)
    VALUES(u[7],offer_id,'LICENSE_ISSUE','DEMO: hỏi về quyền in mô hình','RESOLVED',u[1],
        'Demo license permits personal printing',now()-interval '1 hour');
    INSERT INTO notifications(recipient_user_id,event_type,title,content,related_entity_type,related_entity_id)
    VALUES(u[7],'ORDER_DELIVERED','Đơn hàng demo đã giao','Đơn hàng có snapshot địa chỉ riêng.','PHYSICAL_ORDER',order_paid),
        (u[5],'EARNING_PENDING','Thu nhập demo đang chờ','269100 VND ở bucket PENDING.','SELLER_EARNING',sample_earning_id),
        (u[2],'PRINT_REQUEST','Yêu cầu in demo','Đã in xong, chờ thanh toán phần còn lại.','PRINT_REQUEST',print_id);

    -- Historical token fixtures only: never queue an email for the demo domain.
    INSERT INTO auth_email_tokens(token_id,user_id,recipient,purpose,token_hash,expires_at,used_at,
        delivery_status,delivery_attempts,next_attempt_at,delivered_at,last_error,created_at)
    VALUES
        ('3d000000-0000-4000-8000-00000000d001',u[8],sample_emails[8],'VERIFY_EMAIL',
         'b099b336e331a31f97d3b843e4870843cfc07254507901c5dde2722d0f4f2c4e',now()-interval '1 day',NULL,
         'SENT',1,now()-interval '2 days',now()-interval '2 days',NULL,now()-interval '2 days'),
        ('3d000000-0000-4000-8000-00000000d002',u[8],sample_emails[8],'VERIFY_EMAIL',
         '48147a3d9524bf2dfd6a184d0fe87f7a387071b491dfc748f3acb22f91a69faa',now()-interval '1 day',NULL,
         'FAILED',5,now()-interval '1 day',NULL,'DEMO_PROVIDER_FAILURE',now()-interval '2 days'),
        ('3d000000-0000-4000-8000-00000000d003',u[7],sample_emails[7],'RESET_PASSWORD',
         '673c3c2f0d96a9dd3d700e0bc041631267431d90dbb18dbdcdbf87e9773c2d70',now()-interval '12 hours',now()-interval '1 day',
         'SENT',1,now()-interval '2 days',now()-interval '2 days',NULL,now()-interval '2 days');

    INSERT INTO platform_configs(config_key,config_value,updated_by_user_id,updated_at)
    VALUES('demo.commission.rate','0.10',u[1],now()),
        ('demo.subscription.fee','199000',u[1],now()),
        ('demo.seed.v1','{"users":10,"baseTables":36,"environment":"SANDBOX"}',u[1],now());
END;
$seed$;
