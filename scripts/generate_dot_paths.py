import math

dots = []

# # outer hour dots
# max_dots = 24
# accent_step = 1
# skip_list = [0, 12]
# radius = 80
#
# # inner hour dots
# max_dots = 24
# accent_step = 1
# skip_list = []
# radius = 55
#
# minute dots
max_dots = 60
accent_step = 5
skip_list = []
# skip_list = [0, 1, 29, 30, 31, 59]
radius = 80
#
# # month dots
# max_dots = 24
# accent_step = 1
# skip_list = []
# radius = 55

for d in range(max_dots):
    if d in skip_list:
        print(f'<!-- skip {d} -->')
        continue
    size = 2 if d % accent_step == 0 else 1
    r = 360 / max_dots * d - 90
    x = math.cos(r * math.pi / 180) * radius + 100
    y = math.sin(r * math.pi / 180) * radius + 100
    print(f'<path android:fillColor="@color/white" android:pathData="M{x:.1f} {y:.1f}m{size} 0 a{size} {size} 0 1,0 -{size * 2} 0 a{size} {size} 0 1 0 {size * 2} 0"/>')
