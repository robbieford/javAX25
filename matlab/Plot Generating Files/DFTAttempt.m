fontSize = 25;

fle = wavread('.\..\..\nogit\generated200packets_48000.wav');
y = fle(362001:362500);
x = linspace(1,500,500);

% fle = wavread('.\..\..\nogit\ot3testwnoise4800MonoTrue.wav');
% y = fle(220001:220500);

power1200 = y;
%power1200(1:39) = 0;
power2200 = power1200;
for i=40:500
    samples = y(i-39:i);
    normalFreq = 1200/48000;
    s_prev = 0;
    s_prev2 = 0;
    coeff = 2*cos(2*pi*normalFreq);
    for j=1:40
       s = samples(j)+coeff * s_prev - s_prev2;
       s_prev2 = s_prev;
       s_prev = s;
    end
    power1200(i-20) = s_prev2*s_prev2+s_prev*s_prev-coeff*s_prev*s_prev2;
end
for i=40:500
    samples = y(i-39:i);
    normalFreq = 2200/48000;
    s_prev = 0;
    s_prev2 = 0;
    coeff = 2*cos(2*pi*normalFreq);
    for j=1:40
       s = samples(j)+coeff * s_prev - s_prev2;
       s_prev2 = s_prev;
       s_prev = s;
    end
    power2200(i-20) = s_prev2*s_prev2+s_prev*s_prev-coeff*s_prev*s_prev2;
end
powcombined = (power1200-power2200)/480;
powerNormal = powcombined;
if(powcombined(20) > 0)
    powerNormal(1:20) = 1;
else
    powerNormal(1:20) = -1;
end
for i=20:500
    if (powcombined(i)>0)
        powerNormal(i) = 1;
    else
        powerNormal(i) = -1;
    end
end
f = figure('Position',[0,0,1280,1024]);
set(gcf,'color','w');
plot(x,y,x,powcombined,x, powerNormal);%, x, power1200/480, x, power2200/480);
%filename = 'Goertzel Algorithm Applied to Open Tracker 3 File with Noise';
filename = 'Goertzel Algorithm Applied to 200 Packet Generated File';
title(filename);
xlabel('Sample Number');
ylabel('Magnitude');
minY = min(min(y), min(powerNormal));
maxY = max(max(y), max(powerNormal));
center = (minY+maxY)/ 2;
rangeY = maxY - minY;
adjustedRange = 0.55*rangeY;
minY = (center - adjustedRange);
maxY = (center + adjustedRange);
ylim([minY maxY]);
set(gca,'FontSize',fontSize,'fontWeight','bold');
set(findall(gcf,'type','text'),'FontSize',fontSize,'fontWeight','bold');
xL = get(gca,'XLim');
line(xL,[0 0],'Color','black');
saveas(f, strcat('.\..\..\..\rrxthesis\images\',regexprep(filename,'[^\w'']',''),'.png'));
% pause();
% close(f);
% clear all;