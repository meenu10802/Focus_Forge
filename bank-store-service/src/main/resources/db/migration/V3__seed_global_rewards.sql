-- Starter catalogue of global rewards (admins can edit; players add their own).
INSERT INTO rewards (id, owner_user_id, name, description, emoji, price, stock) VALUES
    (gen_random_uuid(), NULL, 'Guilt-free nap',     '20 minutes, alarm set, zero guilt.',            '😴', 40,  500),
    (gen_random_uuid(), NULL, '30 mins Gaming',     'Half an hour of any game you like.',            '🎮', 50,  500),
    (gen_random_uuid(), NULL, 'One episode',        'One episode of your current show. Just one.',   '📺', 80,  500),
    (gen_random_uuid(), NULL, 'Fancy coffee',       'The overpriced one with the foam art.',         '☕', 120, 200),
    (gen_random_uuid(), NULL, 'Order Biryani',      'Weekend biryani feast. Limited stock!',         '🍛', 300, 5),
    (gen_random_uuid(), NULL, 'Buy that book',      'The one sitting in your cart for three months.', '📚', 600, 2);
